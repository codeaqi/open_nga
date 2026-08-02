package gov.anzong.androidnga.activity.compose.zhihu.data

import android.content.Context
import gov.anzong.androidnga.common.util.NLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 知乎内容预加载。
 *
 * 冷启动和从后台切回前台时在后台悄悄把热搜榜和前几条热搜的回答抓好，
 * 这样用户点进知乎页面时数据已经就绪，不用等。
 *
 * 抓回答要起隐藏 WebView 逐个加载页面，比较重，所以：
 * - 只预热排名靠前的几条，不是整榜；
 * - 串行执行，避免同时开一堆 WebView；
 * - 已有新鲜缓存的直接跳过。
 */
object ZhihuPreloader {

    private const val TAG = "ZhihuPreloader"

    /** 预热前几条热搜的回答 */
    private const val PRELOAD_ANSWER_COUNT = 5

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile
    private var running = false

    /**
     * 开始预加载。重复调用会被忽略，直到上一轮结束。
     */
    fun preload(context: Context) {
        if (running) {
            return
        }
        running = true
        scope.launch {
            try {
                val list = loadHotList()
                if (list.isEmpty()) {
                    return@launch
                }
                preloadAnswers(context, list)
            } catch (e: Exception) {
                NLog.e(TAG, "preload failed: $e")
            } finally {
                running = false
            }
        }
    }

    /** 热搜榜：没有缓存或已过期才去拉 */
    private suspend fun loadHotList(): List<ZhihuHotItem> {
        val cached = ZhihuHotRepository.getCachedList()
        if (cached.isNotEmpty() && !ZhihuHotRepository.isStale()) {
            return cached
        }
        val fresh = withContext(Dispatchers.IO) { ZhihuHotRepository.fetchHotList() }
        NLog.e(TAG, "hot list preloaded: ${fresh.size}")
        return fresh.ifEmpty { cached }
    }

    /**
     * 逐条预热回答。WebView 必须在主线程创建，fetch 内部自己处理，
     * 这里串行 await，避免同时开多个 WebView 抢资源。
     */
    private suspend fun preloadAnswers(context: Context, list: List<ZhihuHotItem>) {
        var done = 0
        for (item in list.take(PRELOAD_ANSWER_COUNT)) {
            val url = item.url
            if (url.isEmpty()) continue
            // 已有新鲜缓存就跳过，别浪费流量
            if (ZhihuCache.getAnswers(url) != null && !ZhihuCache.isStale(url)) {
                continue
            }
            val result = ZhihuAnswerFetcher.fetch(context, url, offset = 0)
            if (result == null || result.answers.isEmpty()) {
                continue
            }
            ZhihuCache.putAnswers(
                url,
                AnswerCacheData(
                    detailBlocks = result.detailBlocks,
                    answers = result.answers,
                    total = result.total,
                    hasMore = !result.isEnd &&
                            (result.total <= 0 || result.answers.size < result.total)
                )
            )
            done++
        }
        NLog.e(TAG, "answers preloaded: $done")
    }
}
