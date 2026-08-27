package sp.phone.task;

import android.os.Handler;
import android.os.Looper;
import android.util.ArrayMap;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;

import gov.anzong.androidnga.base.util.ToastUtils;
import gov.anzong.androidnga.common.util.NLog;
import gov.anzong.androidnga.http.OnHttpCallBack;
import sp.phone.http.bean.ThreadData;
import sp.phone.mvp.model.ArticleListModel;
import sp.phone.param.ArticleListParam;
import sp.phone.rxjava.RxLifecycleProvider;

/**
 * 依次下载帖子的所有页并写入本地缓存。
 *
 * <h3>为什么要限速和退避</h3>
 * 早期版本是「一页回调到了立刻发下一页」，没有任何间隔，出错也照样 {@code page++} 往下走。
 * 在几百页的大帖上会被 NGA 限流，而限流的表现不是返回一句错误文案，是**连续 302 重定向**，
 * OkHttp 跟到第 21 跳才放弃并抛 {@code ProtocolException: Too many follow-up requests: 21}。
 *
 * 也就是说**失败一页要打 21 次请求**，而不是 1 次。实测一个 436 页的帖子有 185 页失败，
 * 光失败的部分就在 5 分钟内发出约 3900 次请求——越被限流敲得越狠，越敲越出不来，
 * 最后缓存里还留下一堆空洞。
 *
 * 现在的策略：
 * <ul>
 *   <li>页与页之间固定间隔 {@link #PAGE_INTERVAL_MS}，把速率压到约 1 页/秒</li>
 *   <li>每 {@link #BATCH_SIZE} 页额外长歇 {@link #BATCH_PAUSE_MS}，避免连续快打累积触发限流</li>
 *   <li>失败不再跳过，而是退避后**重试同一页**，间隔见 {@link #BACKOFF_MS}</li>
 *   <li>同一页连续失败 {@link #MAX_RETRY_PER_PAGE} 次就中止整个任务，
 *       不再顶着限流把剩下几百页跑完</li>
 *   <li>全局串行：同一时刻只跑一个帖子，后点的排队等前一个结束，见 {@link #sQueue}</li>
 * </ul>
 */
public class TopicCacheAllTask {

    private static final String TAG = TopicCacheAllTask.class.getSimpleName();

    /**
     * 正常翻页的间隔，约 1 页/秒。
     *
     * 实测日志里前 24 页几乎不停顿地打完就被限流了，所以这个值宁可保守——
     * 缓存是后台行为，慢几分钟无所谓，被限流则是整个任务作废还白打几千次请求。
     */
    private static final long PAGE_INTERVAL_MS = 1000;

    /** 每跑满这么多页就长歇一次，让服务端的限流窗口有机会滑过去 */
    private static final int BATCH_SIZE = 20;

    /** 每批之间的长休息。比页间隔大一个量级，模拟人翻页时的自然停顿 */
    private static final long BATCH_PAUSE_MS = 5000;

    /**
     * 同一页第 1/2/3 次重试前的等待时间。
     *
     * 这里的失败基本都是限流（表现为 302 重定向链，OkHttp 跟满 21 跳后抛
     * ProtocolException），而限流窗口按秒到几十秒计，2 秒就重试大概率还在窗口里，
     * 等于白打 21 次请求，所以起步就拉到 5 秒。
     */
    private static final long[] BACKOFF_MS = {5000, 15000, 30000};

    /** 同一页连续失败这么多次就认定是限流而不是偶发，中止任务 */
    private static final int MAX_RETRY_PER_PAGE = BACKOFF_MS.length;

    private final ArticleListModel mModel = new ArticleListModel();

    // 任务独立于界面运行，持有自己的 provider 且不发送 DETACH，避免请求被页面销毁打断
    private final RxLifecycleProvider mLifecycleProvider = new RxLifecycleProvider();

    private final Map<String, String> mHeaderMap = new ArrayMap<>();

    // loadPage 的回调在主线程，所以延迟也挂在主线程，不用额外的线程
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private ArticleListParam mBaseParam;

    private int mTotalPage;

    private int mCurrentPage;

    private int mSuccessCount;

    /** 当前这一页已经重试过几次，成功后清零 */
    private int mRetryCount;

    private String mLastError;

    /**
     * 待下载的帖子队列，**队首就是正在跑的那个**（跑完才出队，所以查重能一并覆盖它）。
     *
     * 所有出入队都发生在主线程（菜单点击、以及 loadPage 回调和 Handler 都在主线程），
     * 所以不加锁。
     */
    private static final Queue<ArticleListParam> sQueue = new ArrayDeque<>();

    private static boolean sRunning;

    /**
     * 排队下载某个帖子的全部页。
     *
     * 不直接开跑：上一个帖子还没下完就再发一条请求流，两条叠加起来极易触发限流，
     * 而限流一来两个帖子一起废掉。所以这里只入队，由 {@link #startNext()} 串行调度。
     */
    public static void execute(ArticleListParam param) {
        for (ArticleListParam queued : sQueue) {
            if (queued.tid == param.tid) {
                ToastUtils.info("该帖已在下载队列中");
                return;
            }
        }
        // 存副本，调用方后续改动 param（翻页会改 page）不能影响排队中的任务
        sQueue.add((ArticleListParam) param.clone());
        if (sRunning) {
            ToastUtils.info("已加入下载队列，前面还有 " + (sQueue.size() - 1) + " 个");
            return;
        }
        startNext();
    }

    private static void startNext() {
        ArticleListParam next = sQueue.peek();
        if (next == null) {
            sRunning = false;
            // 队列空了，把限速更新的通道还回去
            TopicCacheUpdateTask.setDownloadRunning(false);
            return;
        }
        sRunning = true;
        // 下载期间让限速更新停手，两条请求流叠加同样会触发限流
        TopicCacheUpdateTask.setDownloadRunning(true);
        new TopicCacheAllTask().start(next);
    }

    /** 当前帖子跑完（无论成功还是中止）：出队，接着跑下一个 */
    private static void onTaskFinished() {
        sQueue.poll();
        startNext();
    }

    private void start(ArticleListParam param) {
        mBaseParam = param;
        mCurrentPage = 1;
        mSuccessCount = 0;
        mRetryCount = 0;
        mModel.setLifecycleProvider(mLifecycleProvider);
        ToastUtils.info("开始缓存全部页...");
        loadCurrentPage();
    }

    private void loadCurrentPage() {
        ArticleListParam param = (ArticleListParam) mBaseParam.clone();
        param.page = mCurrentPage;
        // 必须传非 null 的 header，Retrofit 的 @HeaderMap 不接受 null
        mModel.loadPage(param, mHeaderMap, new OnHttpCallBack<ThreadData>() {
            @Override
            public void onSuccess(ThreadData data) {
                if (mTotalPage == 0) {
                    mTotalPage = Math.max(1, (int) Math.ceil(data.get__ROWS() / 20.0));
                }
                mModel.cachePage(param, data.getRawData(), true);
                mSuccessCount++;
                onPageSucceeded();
            }

            @Override
            public void onError(String text) {
                onPageFailed(param.page, text);
            }

            @Override
            public void onError(String msg, Throwable t) {
                NLog.e(TAG, "cache page " + param.page + " error: " + t);
                onPageFailed(param.page, msg);
            }
        });
    }

    private void onPageSucceeded() {
        mRetryCount = 0;
        mCurrentPage++;
        if (mTotalPage > 0 && mCurrentPage <= mTotalPage) {
            // 刚跑满一批就长歇，其余时候用普通页间隔
            boolean batchDone = (mCurrentPage - 1) % BATCH_SIZE == 0;
            mHandler.postDelayed(this::loadCurrentPage,
                    batchDone ? BATCH_PAUSE_MS : PAGE_INTERVAL_MS);
        } else {
            ToastUtils.success("缓存完成，共" + mSuccessCount + "/" + mTotalPage + "页");
            onTaskFinished();
        }
    }

    /**
     * 失败的页不跳过。退避一段时间后重试同一页，连续失败到上限就整个中止——
     * 继续往下跑只会在限流状态下白白多打几千次请求。
     */
    private void onPageFailed(int page, String error) {
        mLastError = error;
        NLog.e(TAG, "cache page " + page + " failed(" + (mRetryCount + 1) + "): " + error);
        if (mRetryCount >= MAX_RETRY_PER_PAGE) {
            abort(page);
            return;
        }
        long delay = BACKOFF_MS[mRetryCount];
        mRetryCount++;
        mHandler.postDelayed(this::loadCurrentPage, delay);
    }

    private void abort(int page) {
        String reason = mLastError == null ? "未知错误" : mLastError;
        if (mSuccessCount == 0) {
            ToastUtils.error("缓存失败：" + reason);
        } else {
            // 告诉用户停在哪一页，重新点一次「缓存全部」时心里有数
            ToastUtils.error("第" + page + "页连续失败，已停止。已缓存"
                    + mSuccessCount + "页，请稍后再试");
        }
        onTaskFinished();
    }

}
