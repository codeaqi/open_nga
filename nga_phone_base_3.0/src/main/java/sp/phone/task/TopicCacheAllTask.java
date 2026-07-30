package sp.phone.task;

import android.util.ArrayMap;

import java.util.Map;

import gov.anzong.androidnga.base.util.ToastUtils;
import gov.anzong.androidnga.common.util.NLog;
import gov.anzong.androidnga.http.OnHttpCallBack;
import sp.phone.http.bean.ThreadData;
import sp.phone.mvp.model.ArticleListModel;
import sp.phone.param.ArticleListParam;
import sp.phone.rxjava.RxLifecycleProvider;

/**
 * 依次下载帖子的所有页并写入本地缓存，逐页串行请求以避免对服务端造成压力。
 */
public class TopicCacheAllTask {

    private static final String TAG = TopicCacheAllTask.class.getSimpleName();

    private final ArticleListModel mModel = new ArticleListModel();

    // 任务独立于界面运行，持有自己的 provider 且不发送 DETACH，避免请求被页面销毁打断
    private final RxLifecycleProvider mLifecycleProvider = new RxLifecycleProvider();

    private final Map<String, String> mHeaderMap = new ArrayMap<>();

    private ArticleListParam mBaseParam;

    private int mTotalPage;

    private int mCurrentPage;

    private int mSuccessCount;

    private String mLastError;

    public static void execute(ArticleListParam param) {
        new TopicCacheAllTask().start(param);
    }

    private void start(ArticleListParam param) {
        mBaseParam = param;
        mCurrentPage = 1;
        mSuccessCount = 0;
        mModel.setLifecycleProvider(mLifecycleProvider);
        ToastUtils.info("开始缓存全部页...");
        loadNextPage();
    }

    private void loadNextPage() {
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
                onPageDone();
            }

            @Override
            public void onError(String text) {
                NLog.e(TAG, "cache page " + param.page + " failed: " + text);
                mLastError = text;
                onPageDone();
            }

            @Override
            public void onError(String msg, Throwable t) {
                NLog.e(TAG, "cache page " + param.page + " failed: " + msg + ", " + t);
                mLastError = msg;
                onPageDone();
            }
        });
    }

    private void onPageDone() {
        mCurrentPage++;
        if (mTotalPage > 0 && mCurrentPage <= mTotalPage) {
            loadNextPage();
        } else {
            int total = mTotalPage > 0 ? mTotalPage : mCurrentPage - 1;
            if (mSuccessCount == 0) {
                ToastUtils.error("缓存失败：" + (mLastError == null ? "未知错误" : mLastError));
            } else {
                ToastUtils.success("缓存完成，共" + mSuccessCount + "/" + total + "页");
            }
        }
    }

}
