package sp.phone.task;

import android.os.Handler;
import android.os.Looper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import gov.anzong.androidnga.base.util.ToastUtils;
import gov.anzong.androidnga.common.util.NLog;
import gov.anzong.androidnga.favorite.FavoriteStore;
import gov.anzong.androidnga.http.OnHttpCallBack;
import sp.phone.mvp.model.TopicListModel;
import sp.phone.mvp.model.entity.ThreadPageInfo;
import sp.phone.mvp.model.entity.TopicListInfo;
import sp.phone.param.TopicListParam;
import sp.phone.rxjava.RxLifecycleProvider;

/**
 * 把整个收藏夹分页拉一遍，补全本地快照。
 *
 * 节奏和 {@link TopicCacheAllTask} 一致：500ms 页间隔 + 失败退避 + 连续失败中止。
 * 收藏夹页数多时不限速会触发 NGA 限流，而限流的表现是连续 302 重定向，
 * OkHttp 跟到第 21 跳才抛 ProtocolException——失败一页等于打 21 次请求。
 */
public class FavoriteSyncTask {

    private static final String TAG = "FavoriteSyncTask";

    private static final long PAGE_INTERVAL_MS = 500;

    private static final long[] BACKOFF_MS = {2000, 4000, 8000};

    private static boolean sRunning;

    private final TopicListModel mModel = new TopicListModel();

    private final RxLifecycleProvider mLifecycleProvider = new RxLifecycleProvider();

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    /** 本轮见到的全部 tid，只有完整成功才拿它去 prune */
    private final Set<Integer> mSeenTids = new HashSet<>();

    private TopicListParam mParam;

    private Runnable mOnFinished;

    private int mPage = 1;

    private int mRetryCount;

    public static void execute(Runnable onFinished) {
        if (sRunning) {
            ToastUtils.info("正在同步中…");
            return;
        }
        sRunning = true;
        // 同步期间让帖子下载和限速更新停手，两条请求流叠加同样会触发限流
        TopicCacheUpdateTask.setDownloadRunning(true);
        FavoriteSyncTask task = new FavoriteSyncTask();
        task.mOnFinished = onFinished;
        task.start();
    }

    private void start() {
        mParam = new TopicListParam();
        mParam.favor = 1;
        mModel.setLifecycleProvider(mLifecycleProvider);
        ToastUtils.info("开始同步全部收藏…");
        loadCurrentPage();
    }

    private void loadCurrentPage() {
        mModel.loadTopicList(mPage, mParam, new OnHttpCallBack<TopicListInfo>() {
            @Override
            public void onSuccess(TopicListInfo data) {
                List<ThreadPageInfo> pageList = data == null ? null : data.getThreadPageList();
                if (pageList == null || pageList.isEmpty()) {
                    finishSuccessfully();
                    return;
                }
                FavoriteStore.getInstance().upsertAll(pageList);
                for (ThreadPageInfo info : pageList) {
                    mSeenTids.add(info.getTid());
                }
                mRetryCount = 0;
                mPage++;
                mHandler.postDelayed(FavoriteSyncTask.this::loadCurrentPage, PAGE_INTERVAL_MS);
            }

            @Override
            public void onError(String text) {
                onPageFailed(text);
            }

            @Override
            public void onError(String msg, Throwable t) {
                NLog.e(TAG, "sync page " + mPage + " error: " + t);
                onPageFailed(msg);
            }
        });
    }

    private void onPageFailed(String error) {
        NLog.e(TAG, "sync page " + mPage + " failed(" + (mRetryCount + 1) + "): " + error);
        if (mRetryCount >= BACKOFF_MS.length) {
            // **中途失败绝不 prune**：没拉到的收藏会被当成已取消而删掉
            ToastUtils.error("同步中断在第" + mPage + "页，已补全一部分，稍后再试");
            finish();
            return;
        }
        long delay = BACKOFF_MS[mRetryCount];
        mRetryCount++;
        mHandler.postDelayed(this::loadCurrentPage, delay);
    }

    /** 只有完整拉完才按服务端全集清理本地孤儿条目 */
    private void finishSuccessfully() {
        FavoriteStore.getInstance().snapshot().pruneTo(mSeenTids);
        FavoriteStore.getInstance().save();
        ToastUtils.success("同步完成，共" + mSeenTids.size() + "条收藏");
        finish();
    }

    private void finish() {
        sRunning = false;
        TopicCacheUpdateTask.setDownloadRunning(false);
        if (mOnFinished != null) {
            mOnFinished.run();
        }
    }
}
