package sp.phone.task;

import android.os.Handler;
import android.os.Looper;
import android.util.ArrayMap;

import com.alibaba.fastjson.JSON;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import gov.anzong.androidnga.base.util.ContextUtils;
import gov.anzong.androidnga.base.util.ThreadUtils;
import gov.anzong.androidnga.common.util.NLog;
import gov.anzong.androidnga.http.OnHttpCallBack;
import sp.phone.http.bean.ThreadData;
import sp.phone.mvp.model.ArticleListModel;
import sp.phone.mvp.model.entity.ThreadPageInfo;
import sp.phone.param.ArticleListParam;
import sp.phone.rxjava.RxLifecycleProvider;

/**
 * 已缓存帖子的增量更新，只补新增的页。
 *
 * 两种模式：
 *
 * 1. **限速调度**（默认）：app 在前台时每 {@link #TICK_INTERVAL} 只发一个请求，
 *    按 {@link CacheUpdateQueue} 的「最久没检查优先」顺序轮转。目的是把请求摊平在
 *    时间轴上，而不是回到前台时集中打一批。退到后台就暂停，进度不丢。
 *
 * 2. **手动全量**：用户点「检查新回复」时不限速地跑完所有帖子。这里图的是立刻出结果，
 *    按 15 秒一个的话几十个帖子要等十几分钟，没法用。
 *
 * 两种模式互斥：手动跑的时候调度器让路，避免同时写同一个 json。
 */
public class TopicCacheUpdateTask {

    private static final String TAG = TopicCacheUpdateTask.class.getSimpleName();

    private static final int ROWS_PER_PAGE = 20;

    /** 限速模式下两个请求之间的间隔 */
    private static final long TICK_INTERVAL = 15 * 1000L;

    /** 没有帖子到期时最多睡这么久，纯本地判断，不发请求 */
    private static final long IDLE_RECHECK_INTERVAL = 5 * 60 * 1000L;

    /** 单个帖子一轮最多补几页，避免一个热帖独占队列让其他帖挨饿，剩下的下轮继续 */
    private static final int MAX_PAGES_PER_TOPIC = 5;

    /** 连续失败到这个次数就停下，等下次回前台再恢复，免得没网时空转 */
    private static final int MAX_FAIL_STREAK = 3;

    public interface OnUpdateFinishedListener {
        void onUpdateFinished(boolean hasUpdate);
    }

    private static class CachedTopic implements CacheUpdateQueue.Item {

        int tid;

        String topicInfo;

        int cachedPage;

        long lastCheckTime;

        long lastChangeTime;

        @Override
        public long getLastCheckTime() {
            return lastCheckTime;
        }

        @Override
        public long getLastChangeTime() {
            return lastChangeTime;
        }
    }

    // ==================== 调度器入口 ====================

    private static final Handler sHandler = new Handler(Looper.getMainLooper());

    private static TopicCacheUpdateTask sScheduler;

    private static boolean sForeground;

    /** 手动全量占用中，调度器让路 */
    private static boolean sManualRunning;

    /** app 回到前台：恢复限速更新 */
    public static void onEnterForeground() {
        sForeground = true;
        if (sManualRunning) {
            return;
        }
        if (sScheduler == null) {
            sScheduler = new TopicCacheUpdateTask();
        }
        sScheduler.resume();
    }

    /** app 退到后台：暂停，队列和游标都留着，回前台接着跑 */
    public static void onEnterBackground() {
        sForeground = false;
        if (sScheduler != null) {
            sScheduler.pause();
        }
    }

    /**
     * 用户主动触发的全量更新：不限速，把所有缓存帖检查一遍。
     */
    public static void execute(OnUpdateFinishedListener listener) {
        if (sManualRunning) {
            NLog.d(TAG, "manual update already running, skip");
            return;
        }
        sManualRunning = true;
        if (sScheduler != null) {
            sScheduler.pause();
        }
        TopicCacheUpdateTask task = new TopicCacheUpdateTask();
        task.mPaced = false;
        task.startFull(listener);
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

    // ==================== 实例状态 ====================

    private final ArticleListModel mModel = new ArticleListModel();

    private final RxLifecycleProvider mLifecycleProvider = new RxLifecycleProvider();

    private final Map<String, String> mHeaderMap = new ArrayMap<>();

    private final Runnable mTick = this::tick;

    private final Runnable mRescan = this::resume;

    /** true=限速调度，false=手动全量 */
    private boolean mPaced = true;

    private List<CachedTopic> mQueue;

    /** 正在补页的帖子，为 null 表示该挑下一个了 */
    private CachedTopic mCurrent;

    private int mCurrentPage;

    private int mTargetPage;

    private int mFailStreak;

    /** 手动全量模式的游标 */
    private int mTopicIndex;

    private boolean mHasUpdate;

    private OnUpdateFinishedListener mListener;

    // ==================== 限速调度 ====================

    private void resume() {
        if (!sForeground || sManualRunning) {
            return;
        }
        mFailStreak = 0;
        sHandler.removeCallbacks(mTick);
        sHandler.removeCallbacks(mRescan);
        if (mQueue != null) {
            scheduleTick(0);
            return;
        }
        mModel.setLifecycleProvider(mLifecycleProvider);
        // 扫描磁盘放到子线程，避免阻塞启动
        ThreadUtils.postOnSubThread(() -> {
            List<CachedTopic> topics = scanCachedTopics();
            ThreadUtils.postOnMainThread(() -> {
                mQueue = topics;
                NLog.d(TAG, "scheduler queue size=" + topics.size());
                scheduleTick(0);
            });
        });
    }

    private void pause() {
        sHandler.removeCallbacks(mTick);
        sHandler.removeCallbacks(mRescan);
    }

    private void scheduleTick(long delay) {
        if (!sForeground || sManualRunning) {
            return;
        }
        sHandler.removeCallbacks(mTick);
        sHandler.postDelayed(mTick, delay);
    }

    private void tick() {
        if (!sForeground || sManualRunning || mQueue == null) {
            return;
        }
        // 还在补当前帖子的后续页，继续补——补页同样走限速通道，
        // 否则一个新增几十页的热帖会瞬间打出几十个请求
        if (mCurrent != null) {
            loadPage(mCurrent);
            return;
        }
        long now = System.currentTimeMillis();
        CachedTopic next = CacheUpdateQueue.pickNext(mQueue, now);
        if (next == null) {
            // 这一轮都查完了。睡到最近一个到期时刻，醒来重扫一遍磁盘，
            // 把这期间新缓存的帖子也纳进队列
            long delay = CacheUpdateQueue.nextDueDelay(mQueue, now, IDLE_RECHECK_INTERVAL);
            delay = Math.min(Math.max(delay, TICK_INTERVAL), IDLE_RECHECK_INTERVAL);
            NLog.d(TAG, "nothing due, rescan in " + delay / 1000 + "s");
            mQueue = null;
            sHandler.postDelayed(mRescan, delay);
            return;
        }
        mCurrent = next;
        // 从已缓存的最后一页开始重下，那一页通常未满，可能有新回复
        mCurrentPage = next.cachedPage;
        mTargetPage = 0;
        loadPage(next);
    }

    // ==================== 手动全量 ====================

    private void startFull(OnUpdateFinishedListener listener) {
        mListener = listener;
        mModel.setLifecycleProvider(mLifecycleProvider);
        ThreadUtils.postOnSubThread(() -> {
            List<CachedTopic> topics = scanCachedTopics();
            ThreadUtils.postOnMainThread(() -> {
                mQueue = topics;
                mTopicIndex = 0;
                nextTopic();
            });
        });
    }

    private void nextTopic() {
        if (mQueue == null || mTopicIndex >= mQueue.size()) {
            finishFull();
            return;
        }
        CachedTopic topic = mQueue.get(mTopicIndex);
        mCurrent = topic;
        mCurrentPage = topic.cachedPage;
        mTargetPage = 0;
        loadPage(topic);
    }

    private void finishFull() {
        sManualRunning = false;
        if (mListener != null) {
            mListener.onUpdateFinished(mHasUpdate);
        }
        // 手动跑完，把调度器放回去接着限速轮转
        if (sForeground) {
            onEnterForeground();
        }
    }

    // ==================== 两种模式共用 ====================

    private void loadPage(CachedTopic topic) {
        ArticleListParam param = new ArticleListParam();
        param.tid = topic.tid;
        param.page = mCurrentPage;
        param.topicInfo = topic.topicInfo;

        mModel.loadPage(param, mHeaderMap, new OnHttpCallBack<ThreadData>() {
            @Override
            public void onSuccess(ThreadData data) {
                mFailStreak = 0;
                if (mTargetPage == 0) {
                    int latestPage = Math.max(1,
                            (int) Math.ceil(data.get__ROWS() / (double) ROWS_PER_PAGE));
                    mTargetPage = mPaced
                            ? Math.min(latestPage, mCurrentPage + MAX_PAGES_PER_TOPIC - 1)
                            : latestPage;
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
                onTopicFailed(topic);
            }

            @Override
            public void onError(String msg, Throwable t) {
                onError(msg);
            }
        });
    }

    /**
     * 刷新缓存的帖子描述文件：最新回复数、未读计数，以及排队用的两个时间戳。
     * 时间戳必须落盘，否则重启后队列顺序就丢了。
     */
    private void updateTopicInfo(CachedTopic topic, ThreadData data) {
        ThreadPageInfo pageInfo = JSON.parseObject(topic.topicInfo, ThreadPageInfo.class);
        if (pageInfo == null) {
            return;
        }
        long now = System.currentTimeMillis();
        int increased = data.get__ROWS() - pageInfo.getReplies();
        if (increased > 0) {
            // 累加而非覆盖，用户未查看期间的多次更新要合并计数
            pageInfo.setNewReplyCount(pageInfo.getNewReplyCount() + increased);
            pageInfo.setLastChangeTime(now);
            mHasUpdate = true;
        } else if (pageInfo.getLastChangeTime() <= 0) {
            // 首次检查且没有新回复：把观测起点定在现在，
            // 否则 0 会一直被当作「未观测」，老帖永远降不了频
            pageInfo.setLastChangeTime(now);
        }
        pageInfo.setReplies(data.get__ROWS());
        if (data.getThreadInfo() != null) {
            pageInfo.setLastPoster(data.getThreadInfo().getLastPoster());
        }
        pageInfo.setLastCheckTime(now);
        topic.lastCheckTime = now;
        topic.lastChangeTime = pageInfo.getLastChangeTime();
        topic.topicInfo = JSON.toJSONString(pageInfo);
    }

    private void onPageDone(CachedTopic topic) {
        mCurrentPage++;
        if (mCurrentPage <= mTargetPage) {
            if (mPaced) {
                scheduleTick(TICK_INTERVAL);
            } else {
                loadPage(topic);
            }
            return;
        }
        topic.cachedPage = Math.max(topic.cachedPage, mTargetPage);
        mCurrent = null;
        if (mPaced) {
            scheduleTick(TICK_INTERVAL);
        } else {
            mTopicIndex++;
            nextTopic();
        }
    }

    private void onTopicFailed(CachedTopic topic) {
        mFailStreak++;
        // 失败也要刷新检查时间，否则失败的帖子会一直卡在队首反复重试，
        // 把整个队列的配额吃光
        markChecked(topic);
        mCurrent = null;
        if (!mPaced) {
            mTopicIndex++;
            nextTopic();
            return;
        }
        if (mFailStreak >= MAX_FAIL_STREAK) {
            NLog.d(TAG, "too many failures, pause until next foreground");
            pause();
            return;
        }
        scheduleTick(TICK_INTERVAL);
    }

    /** 请求失败时只把「已检查」时间落盘，不动其他字段 */
    private void markChecked(CachedTopic topic) {
        long now = System.currentTimeMillis();
        topic.lastCheckTime = now;
        ThreadUtils.postOnSubThread(() -> {
            try {
                File infoFile = new File(getCacheDir(String.valueOf(topic.tid)), topic.tid + ".json");
                if (!infoFile.exists()) {
                    return;
                }
                ThreadPageInfo pageInfo = JSON.parseObject(
                        FileUtils.readFileToString(infoFile), ThreadPageInfo.class);
                if (pageInfo == null) {
                    return;
                }
                pageInfo.setLastCheckTime(now);
                FileUtils.write(infoFile, JSON.toJSONString(pageInfo));
            } catch (Exception e) {
                NLog.e(TAG, "mark checked failed: " + e);
            }
        });
    }

    private static File getCacheDir(String tid) {
        return new File(ContextUtils.getContext().getFilesDir().getAbsolutePath() + "/cache/" + tid);
    }

    /**
     * 扫描缓存目录，收集每个帖子已缓存到第几页以及排队用的时间戳
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
                topic.lastCheckTime = pageInfo.getLastCheckTime();
                topic.lastChangeTime = pageInfo.getLastChangeTime();
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
}
