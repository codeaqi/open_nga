package gov.anzong.androidnga;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Process;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import com.alibaba.android.arouter.launcher.ARouter;
import com.justwen.androidnga.base.network.retrofit.RetrofitHelper;
import com.justwen.androidnga.cloud.CloudServerManager;
import com.justwent.androidnga.bu.UserManager;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

import gov.anzong.androidnga.activity.compose.board.ForumBoardViewModel;
import gov.anzong.androidnga.activity.compose.zhihu.data.ZhihuPreloader;
import gov.anzong.androidnga.base.logger.Logger;
import gov.anzong.androidnga.base.util.ContextUtils;
import gov.anzong.androidnga.base.util.PreferenceUtils;
import gov.anzong.androidnga.base.util.ThreadUtils;
import gov.anzong.androidnga.common.PreferenceKey;
import gov.anzong.androidnga.common.util.NLog;
import gov.anzong.androidnga.common.util.ReflectUtils;
import gov.anzong.androidnga.db.AppDatabase;
import sp.phone.common.UserManagerImpl;
import sp.phone.common.VersionUpgradeHelper;
import sp.phone.task.CheckInTask;
import sp.phone.task.TopicCacheUpdateTask;

public class NgaClientApp extends Application {

    private static final String TAG = NgaClientApp.class.getSimpleName();

    /** 缓存更新的启动延迟，让出冷启动阶段的资源 */
    private static final long DELAY_UPDATE_CACHE = 5000L;

    /** 知乎预热比帖子缓存更晚一点，避免和启动、缓存更新抢资源 */
    private static final long DELAY_PRELOAD_ZHIHU = 8000L;

    private static boolean sNewVersion;

    @Override
    public void onCreate() {
        ContextUtils.setApplication(this);
        initLogger();
        PreferenceUtils.transfer(this);
        checkNewVersion();
        VersionUpgradeHelper.upgrade();
        AppDatabase.init(this);
        initCoreModule();
        initRouter();
        checkIn();
        registerForegroundCacheUpdate();
        super.onCreate();

        // fixWebViewMultiProcessException();
        CloudServerManager.init(this);
        Thread.setDefaultUncaughtExceptionHandler(new ExceptionHandlerProxy(Thread.getDefaultUncaughtExceptionHandler()));
    }

    private void initLogger() {
        Logger.setBuildDebugMode(BuildConfig.DEBUG);
        Logger.d(TAG, "app nga android start");
        NLog.setDebug(BuildConfig.DEBUG);
    }

    private void fixWebViewMultiProcessException() {
        try {
            File dataDir = getDataDir();
            File[] dirs = dataDir.listFiles();

            Object ppidObj = ReflectUtils.invokeMethod(Process.class, "myPpid");

            int ppid = ppidObj != null ? (int) ppidObj : Process.myPid();

            if (dirs != null) {
                for (File dir : dirs) {
                    if (dir.getName().contains("webview")) {
                        if (!dir.getName().contains("webview_" + ppid)) {
                            ThreadUtils.postOnSubThread(() -> {
                                try {
                                    FileUtils.deleteDirectory(dir);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            });

                        }
                    }
                }
            }

            WebView.setDataDirectorySuffix(String.valueOf(ppid));
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

    }

    private void initRouter() {
        if (BuildConfig.DEBUG) {   // 这两行必须写在init之前，否则这些配置在init过程中将无效
            ARouter.openLog();     // 打印日志
            ARouter.openDebug();   // 开启调试模式(如果在InstantRun模式下运行，必须开启调试模式！线上版本需要关闭,否则有安全风险)
        }
        ARouter.init(this); // 尽可能早，推荐在Application中初始化
    }

    private void initCoreModule() {
        UserManagerImpl.getInstance().initialize(this);
        UserManager.INSTANCE.getActiveUser();
        RetrofitHelper.setCookieProvider(() -> UserManagerImpl.getInstance().getCookie());
//        // 注册crashHandler
//        CrashHandler.getInstance().init(this);
        initCoreModuleAsync();

    }

    private void initCoreModuleAsync() {
        ThreadUtils.postOnSubThread(new Runnable() {
            @Override
            public void run() {
                ForumBoardViewModel.INSTANCE.getBoardLiveData();
            }
        });
    }

    /**
     * 增量更新已缓存的帖子。调度器只在前台跑，每 15 秒一个请求慢慢轮转，
     * 退到后台就暂停、进度保留；延迟启动以错开启动高峰。
     */
    private void registerForegroundCacheUpdate() {
        registerActivityLifecycleCallbacks(new SimpleActivityLifecycleCallbacks() {

            private int mStartedCount;

            @Override
            public void onActivityStarted(@NonNull Activity activity) {
                mStartedCount++;
                if (mStartedCount == 1) {
                    // 数量从 0 变 1，说明刚回到前台（冷启动首次打开界面也属于这种情况）
                    ThreadUtils.postOnMainThreadDelay(
                            TopicCacheUpdateTask::onEnterForeground,
                            DELAY_UPDATE_CACHE);
                    // 预热知乎热搜和前几条的回答，用户点进去时直接就有内容。
                    // 内部自己判断缓存是否过期，不会每次都真的联网。
                    ThreadUtils.postOnMainThreadDelay(
                            () -> ZhihuPreloader.INSTANCE.preload(NgaClientApp.this),
                            DELAY_PRELOAD_ZHIHU);
                }
            }

            @Override
            public void onActivityStopped(@NonNull Activity activity) {
                mStartedCount--;
                if (mStartedCount == 0) {
                    TopicCacheUpdateTask.onEnterBackground();
                }
            }
        });
    }

    /**
     * 只关心前后台切换，其余回调留空
     */
    private abstract static class SimpleActivityLifecycleCallbacks implements ActivityLifecycleCallbacks {

        @Override
        public void onActivityCreated(@NonNull Activity activity, Bundle savedInstanceState) {
        }

        @Override
        public void onActivityResumed(@NonNull Activity activity) {
        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {
        }
    }

    private void checkNewVersion() {
        int versionCode = PreferenceUtils.getData(PreferenceKey.VERSION_CODE, 0);
        if (BuildConfig.VERSION_CODE > versionCode) {
            PreferenceUtils.putData(PreferenceKey.PREVIOUS_VERSION_CODE, versionCode);
            PreferenceUtils.putData(PreferenceKey.VERSION_CODE, BuildConfig.VERSION_CODE);
            sNewVersion = true;
            PreferenceUtils.putData(PreferenceKey.KEY_WEBVIEW_DATA_INDEX, 0);
        }
    }

    private void checkIn() {
        CheckInTask.autoCheckIn(this);
    }

    public static boolean isNewVersion() {
        return sNewVersion;
    }

    public static void setNewVersion(boolean newVersion) {
        sNewVersion = newVersion;
    }

}
