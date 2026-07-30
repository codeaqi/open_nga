package sp.phone.task;

import android.util.ArrayMap;

import com.alibaba.fastjson.JSON;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import gov.anzong.androidnga.base.util.ContextUtils;
import gov.anzong.androidnga.base.util.PreferenceUtils;
import gov.anzong.androidnga.base.util.ThreadUtils;
import gov.anzong.androidnga.common.util.NLog;
import gov.anzong.androidnga.http.OnHttpCallBack;
import sp.phone.http.bean.ThreadData;
import sp.phone.mvp.model.ArticleListModel;
import sp.phone.mvp.model.entity.ThreadPageInfo;
import sp.phone.param.ArticleListParam;
import sp.phone.rxjava.RxLifecycleProvider;

/**
 * 增量更新已缓存的帖子：只补齐新增的页，用于帖子有新回复的场景。
 * 串行执行，静默运行，不打扰用户。
 */
public class TopicCacheUpdateTask {

    private static final String TAG = TopicCacheUpdateTask.class.getSimpleName();

    private static final int ROWS_PER_PAGE = 20;

    /** 两次自动更新之间的最小间隔 */
    private static final long MIN_UPDATE_INTERVAL = 60 * 60 * 1000L;

    private static final String KEY_LAST_UPDATE_TIME = "topic_cache_last_update_time";

    private final ArticleListModel mModel = new ArticleListModel();

    private final RxLifecycleProvider mLifecycleProvider = new RxLifecycleProvider();

    private final Map<String, String> mHeaderMap = new ArrayMap<>();

    /** 待更新的帖子队列 */
    private List<CachedTopic> mTopics;

    private int mTopicIndex;

    /** 当前帖子正在下载的页码 */
    private int mCurrentPage;

    /** 当前帖子需要下载到第几页 */
    private int mTargetPage;

    private OnUpdateFinishedListener mListener;

    public interface OnUpdateFinishedListener {
        void onUpdateFinished(boolean hasUpdate);
    }

    private static class CachedTopic {
        int tid;
        String topicInfo;
        int cachedPage;
    }

    private boolean mHasUpdate;

    /** 是否有更新任务正在执行，避免启动和切前台同时触发导致重复请求 */
    private static volatile boolean sRunning;

    public static void execute(OnUpdateFinishedListener listener) {
        if (sRunning) {
            NLog.d(TAG, "update already running, skip");
            return;
        }
        sRunning = true;
        new TopicCacheUpdateTask().start(listener);
    }

    /**
     * 距上次更新超过 {@link #MIN_UPDATE_INTERVAL} 才执行，用于回到前台时的检查
     */
    public static void executeIfExpired(OnUpdateFinishedListener listener) {
        long last = PreferenceUtils.getData(KEY_LAST_UPDATE_TIME, 0L);
        long elapsed = System.currentTimeMillis() - last;
        if (elapsed < MIN_UPDATE_INTERVAL) {
            NLog.d(TAG, "last update was " + elapsed / 1000 + "s ago, skip");
            return;
        }
        execute(listener);
    }

    private void start(OnUpdateFinishedListener listener) {
        mListener = listener;
        mModel.setLifecycleProvider(mLifecycleProvider);
        // 扫描磁盘放到子线程，避免阻塞启动
        ThreadUtils.postOnSubThread(() -> {
            mTopics = scanCachedTopics();
            ThreadUtils.postOnMainThread(() -> {
                mTopicIndex = 0;
                nextTopic();
            });
        });
    }

    /**
     * 清除某个帖子的新回复标记，在用户查看该帖后调用
     */
    public static void clearNewReplyTag(int tid) {
        ThreadUtils.postOnSubThread(() -> {
            File infoFile = new File(getCacheDir(String.valueOf(tid)), tid + ".json");
            if (!infoFile.exists()) {
                return;
            }
            try {
                ThreadPageInfo pageInfo = JSON.parseObject(
                        FileUtils.readFileToString(infoFile), ThreadPageInfo.class);
                if (pageInfo == null || pageInfo.getNewReplyCount() == 0) {
                    return;
                }
                pageInfo.setNewReplyCount(0);
                FileUtils.write(infoFile, JSON.toJSONString(pageInfo));
            } catch (Exception e) {
                NLog.e(TAG, "clear new reply tag failed: " + e);
            }
        });
    }

    private static File getCacheDir(String tid) {
        return new File(ContextUtils.getContext().getFilesDir().getAbsolutePath() + "/cache/" + tid);
    }

    /**
     * 扫描缓存目录，收集每个帖子已缓存到第几页
     */
    private List<CachedTopic> scanCachedTopics() {
        List<CachedTopic> topics = new ArrayList<>();
        String path = ContextUtils.getContext().getFilesDir().getAbsolutePath() + "/cache/";
        File[] cacheDirs = new File(path).listFiles();
        if (cacheDirs == null) {
            return topics;
        }
        for (File dir : cacheDirs) {
            if (!dir.isDirectory()) {
                continue;
            }
            String tidStr = dir.getName();
            File infoFile = new File(dir, tidStr + ".json");
            if (!infoFile.exists()) {
                continue;
            }
            try {
                String topicInfo = FileUtils.readFileToString(infoFile);
                ThreadPageInfo pageInfo = JSON.parseObject(topicInfo, ThreadPageInfo.class);
                if (pageInfo == null) {
                    continue;
                }
                CachedTopic topic = new CachedTopic();
                topic.tid = Integer.parseInt(tidStr);
                topic.topicInfo = topicInfo;
                topic.cachedPage = findMaxCachedPage(dir, tidStr);
                if (topic.cachedPage > 0) {
                    topics.add(topic);
                }
            } catch (Exception e) {
                NLog.e(TAG, "skip invalid cache dir " + tidStr + ": " + e);
            }
        }
        return topics;
    }

    /**
     * 找出该帖子已缓存的最大页码，页文件形如 1.json、2.json
     */
    private int findMaxCachedPage(File dir, String tidStr) {
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        int maxPage = 0;
        for (File file : files) {
            String name = file.getName();
            if (!name.endsWith(".json") || name.startsWith(tidStr)) {
                continue;
            }
            try {
                maxPage = Math.max(maxPage, Integer.parseInt(name.replace(".json", "")));
            } catch (NumberFormatException e) {
                // 非页码文件，忽略
            }
        }
        return maxPage;
    }

    private void nextTopic() {
        if (mTopics == null || mTopicIndex >= mTopics.size()) {
            PreferenceUtils.putData(KEY_LAST_UPDATE_TIME, System.currentTimeMillis());
            sRunning = false;
            if (mListener != null) {
                mListener.onUpdateFinished(mHasUpdate);
            }
            return;
        }
        CachedTopic topic = mTopics.get(mTopicIndex);
        // 从已缓存的最后一页开始重下，那一页通常未满，可能有新回复
        mCurrentPage = topic.cachedPage;
        mTargetPage = 0;
        loadPage(topic);
    }

    private void loadPage(CachedTopic topic) {
        ArticleListParam param = new ArticleListParam();
        param.tid = topic.tid;
        param.page = mCurrentPage;
        param.topicInfo = topic.topicInfo;

        mModel.loadPage(param, mHeaderMap, new OnHttpCallBack<ThreadData>() {
            @Override
            public void onSuccess(ThreadData data) {
                if (mTargetPage == 0) {
                    mTargetPage = Math.max(1, (int) Math.ceil(data.get__ROWS() / (double) ROWS_PER_PAGE));
                    // 帖子信息里的回复数也要刷新，缓存列表才会显示最新数字
                    updateTopicInfo(topic, data);
                    // topicInfo 在上面被刷新过，同步给本次写入
                    param.topicInfo = topic.topicInfo;
                }
                mModel.cachePage(param, data.getRawData(), true);
                onPageDone(topic);
            }

            @Override
            public void onError(String text) {
                NLog.e(TAG, "update tid " + topic.tid + " page " + mCurrentPage + " failed: " + text);
                // 单个帖子失败不影响其他帖子，直接跳到下一个
                nextTopicIndex();
            }

            @Override
            public void onError(String msg, Throwable t) {
                onError(msg);
            }
        });
    }

    /**
     * 用最新回复数覆盖缓存的帖子描述文件，并累计未读的新回复数
     */
    private void updateTopicInfo(CachedTopic topic, ThreadData data) {
        ThreadPageInfo pageInfo = JSON.parseObject(topic.topicInfo, ThreadPageInfo.class);
        if (pageInfo == null || data.getThreadInfo() == null) {
            return;
        }
        int increased = data.get__ROWS() - pageInfo.getReplies();
        if (increased > 0) {
            // 累加而非覆盖，用户未查看期间的多次更新要合并计数
            pageInfo.setNewReplyCount(pageInfo.getNewReplyCount() + increased);
            mHasUpdate = true;
        }
        pageInfo.setReplies(data.get__ROWS());
        pageInfo.setLastPoster(data.getThreadInfo().getLastPoster());
        topic.topicInfo = JSON.toJSONString(pageInfo);
    }

    private void onPageDone(CachedTopic topic) {
        mCurrentPage++;
        if (mCurrentPage <= mTargetPage) {
            loadPage(topic);
        } else {
            nextTopicIndex();
        }
    }

    private void nextTopicIndex() {
        mTopicIndex++;
        nextTopic();
    }

}
