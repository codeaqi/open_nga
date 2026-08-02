package gov.anzong.androidnga.activity.compose.zhihu.data

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import gov.anzong.androidnga.base.util.ContextUtils
import gov.anzong.androidnga.common.util.NLog
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 知乎热搜数据源。
 *
 * 知乎官方热搜接口（hot-lists/total）现在要求登录态，所以改用第三方聚合接口，
 * 内置多个源按顺序尝试，谁先返回有效数据就用谁。与 NGA 的网络栈相互独立。
 */
object ZhihuHotRepository {

    private const val TAG = "ZhihuHotRepository"

    /**
     * 数据源按优先级排列。原来的三个免费聚合 API 在 2026 年 8 月全部不可用
     * （imsyy.top DNS 解析失败、vvhan/oioweb 连接超时），这里换成验证过能用的。
     *
     * 60s.viki.moe 返回 link（真实 question 链接）和 detail（问题正文，
     * 平均 600 字），所以详情页可以直接用本地数据渲染，不必加载知乎网页——
     * 知乎对非官方客户端的 question 页会直接返回 403，WebView 里只能看到
     * 登录墙和「打开知乎 App」引流，登录了也一样。
     */
    private val SOURCES = listOf(
        "https://60s.viki.moe/v2/zhihu"
    )

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private const val REFERER = "https://www.zhihu.com/"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    /** 热搜列表缓存。热搜更新频率低，几分钟内重复进页面直接用缓存，不必再联网。 */
    @Volatile
    private var cachedList: List<ZhihuHotItem> = emptyList()

    @Volatile
    private var cachedAt = 0L

    /** 超过这个时间就认为榜单旧了，需要后台刷新（但仍先展示旧的） */
    private const val STALE_MS = 30 * 60 * 1000L

    private val cacheFile: File by lazy {
        File(ContextUtils.getContext().cacheDir, "zhihu").apply { mkdirs() }
            .let { File(it, "hot_list.json") }
    }

    /**
     * 缓存里的热搜，内存没有就读磁盘（杀掉后台重进也能立刻有内容）。
     * 这里**不判断过期**，过期的也返回，由调用方先展示、再后台刷新。
     */
    @Synchronized
    fun getCachedList(): List<ZhihuHotItem> {
        if (cachedList.isNotEmpty()) return cachedList
        loadFromDisk()
        return cachedList
    }

    /** 榜单是否已经旧了，需要后台刷新 */
    @Synchronized
    fun isStale(): Boolean = System.currentTimeMillis() - cachedAt > STALE_MS

    private fun loadFromDisk() {
        try {
            if (!cacheFile.exists()) return
            val root = JSON.parseObject(cacheFile.readText()) ?: return
            val items = root.getJSONArray("items")?.mapNotNull { item ->
                val o = item as? JSONObject ?: return@mapNotNull null
                ZhihuHotItem(
                    rank = o.getIntValue("rank"),
                    title = o.getString("title").orEmpty(),
                    excerpt = o.getString("excerpt").orEmpty(),
                    detailText = o.getString("detailText").orEmpty(),
                    content = o.getString("content").orEmpty(),
                    answerCount = o.getIntValue("answerCount"),
                    followerCount = o.getIntValue("followerCount"),
                    heat = 0L,
                    trend = "normal",
                    url = o.getString("url").orEmpty()
                )
            }?.filter { it.title.isNotEmpty() }.orEmpty()
            if (items.isNotEmpty()) {
                cachedList = items
                cachedAt = root.getLongValue("at")
            }
        } catch (e: Exception) {
            NLog.e(TAG, "load hot cache failed: $e")
        }
    }

    private fun saveToDisk(items: List<ZhihuHotItem>, at: Long) {
        try {
            val root = JSONObject()
            root["at"] = at
            root["items"] = items.map { i ->
                JSONObject().apply {
                    this["rank"] = i.rank
                    this["title"] = i.title
                    this["excerpt"] = i.excerpt
                    this["detailText"] = i.detailText
                    this["content"] = i.content
                    this["answerCount"] = i.answerCount
                    this["followerCount"] = i.followerCount
                    this["url"] = i.url
                }
            }
            cacheFile.writeText(root.toJSONString())
        } catch (e: Exception) {
            NLog.e(TAG, "save hot cache failed: $e")
        }
    }

    /**
     * 拉取知乎热搜榜。逐个源尝试，全部失败返回空列表，由调用方决定提示。
     */
    fun fetchHotList(): List<ZhihuHotItem> {
        for (url in SOURCES) {
            val raw = request(url) ?: continue
            val items = parse(raw)
            if (items.isNotEmpty()) {
                NLog.e(TAG, "source ok: $url, ${items.size} items")
                val now = System.currentTimeMillis()
                cachedList = items
                cachedAt = now
                saveToDisk(items, now)
                return items
            }
            NLog.e(TAG, "empty result from $url")
        }
        return emptyList()
    }

    private fun request(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", REFERER)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    NLog.e(TAG, "request $url failed: ${response.code}")
                    return null
                }
                response.body?.string()
            }
        } catch (e: Exception) {
            NLog.e(TAG, "request $url error: $e")
            null
        }
    }

    /**
     * 兼容不同聚合源的返回结构。通用字段：title / link（原文链接）/
     * detail（问题正文）/ hot_value_desc（热度文案）/ index（排名，缺省按数组下标）。
     * 取不到 title 的条目直接跳过。
     *
     * 注意：这里不再给拿不到链接的条目拼「知乎搜索页」兜底。搜索页是知乎强制登录
     * 最严的入口，点进去只会看到登录墙；宁可让 url 为空，由详情页隐藏跳转入口。
     */
    private fun parse(raw: String): List<ZhihuHotItem> {
        val result = mutableListOf<ZhihuHotItem>()
        try {
            val root = JSON.parseObject(raw) ?: return emptyList()
            val data = findDataArray(root) ?: return emptyList()
            data.forEachIndexed { position, item ->
                val obj = item as? JSONObject ?: return@forEachIndexed
                val title = obj.getString("title") ?: return@forEachIndexed
                // 60s.viki.moe 用 link 存原文链接，其它源可能是 url / mobileUrl
                val url = (obj.getString("link")
                    ?: obj.getString("url")
                    ?: obj.getString("mobileUrl"))
                    ?.takeIf { it.isNotEmpty() }
                    ?: ""
                val rank = if (obj.containsKey("index")) {
                    obj.getIntValue("index")
                } else if (obj.containsKey("position")) {
                    obj.getIntValue("position")
                } else {
                    position + 1
                }
                // 60s.viki.moe 的热度在 hot_value_desc，detail 是问题正文
                val detailText = obj.getString("hot_value_desc")
                    ?: obj.getString("hot")
                    ?: obj.getString("detailText")
                    ?: ""
                val content = obj.getString("detail")
                    ?: obj.getString("desc")
                    ?: ""
                result.add(
                    ZhihuHotItem(
                        rank = rank,
                        title = title,
                        excerpt = obj.getString("desc") ?: "",
                        detailText = detailText,
                        content = content,
                        answerCount = obj.getIntValue("answer_cnt"),
                        followerCount = obj.getIntValue("follower_cnt"),
                        heat = 0L,
                        trend = "normal",
                        url = url
                    )
                )
            }
        } catch (e: Exception) {
            NLog.e(TAG, "parse failed: $e")
            return emptyList()
        }
        return result
    }

    /** 从不同源的 JSON 里找出热榜数组：根 data / result.data */
    private fun findDataArray(root: JSONObject): JSONArray? {
        root.getJSONArray("data")?.let { return it }
        return root.getJSONObject("result")?.getJSONArray("data")
    }
}
