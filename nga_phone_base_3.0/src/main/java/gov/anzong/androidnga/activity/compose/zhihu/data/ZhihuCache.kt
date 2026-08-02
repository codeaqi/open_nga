package gov.anzong.androidnga.activity.compose.zhihu.data

import android.content.Context
import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import gov.anzong.androidnga.base.util.ContextUtils
import gov.anzong.androidnga.common.util.NLog
import java.io.File

/**
 * 回答和评论的缓存，内存 + 磁盘两级。
 *
 * 抓一次要起隐藏 WebView 加载整个知乎页面，好几秒，所以：
 * - 进程内重复打开走内存缓存，瞬开；
 * - 杀掉后台再进来走磁盘缓存，先把上次的内容显示出来，
 *   再在后台悄悄拉新的（见 [isStale]），不让用户对着转圈等。
 */
object ZhihuCache {

    private const val TAG = "ZhihuCache"

    /** 超过这个时间就认为内容旧了，需要后台刷新（但仍先展示旧内容） */
    private const val STALE_MS = 30 * 60 * 1000L

    /** 磁盘缓存最多保留多少个问题 */
    private const val MAX_DISK_FILES = 40

    /** 内存里最多缓存多少个问题 */
    private const val MAX_ENTRIES = 30

    private data class Entry<T>(val data: T, val at: Long)

    private val answerCache = object : LinkedHashMap<String, Entry<AnswerCacheData>>(
        16, 0.75f, true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, Entry<AnswerCacheData>>?
        ): Boolean = size > MAX_ENTRIES
    }

    private val commentCache = object : LinkedHashMap<String, Entry<List<ZhihuComment>>>(
        16, 0.75f, true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, Entry<List<ZhihuComment>>>?
        ): Boolean = size > MAX_ENTRIES * 4
    }

    private val cacheDir: File by lazy {
        File(ContextUtils.getContext().cacheDir, "zhihu").apply { mkdirs() }
    }

    // ---------------- 回答 ----------------

    /**
     * 取缓存的回答，内存没有就读磁盘。返回 null 表示彻底没有。
     * 注意这里**不判断过期**——过期的也照样返回，由调用方先展示、再后台刷新。
     */
    @Synchronized
    fun getAnswers(url: String): AnswerCacheData? {
        answerCache[url]?.let { return it.data }
        val fromDisk = readAnswersFromDisk(url) ?: return null
        answerCache[url] = fromDisk
        return fromDisk.data
    }

    /** 缓存是否已经旧了，需要后台刷新 */
    @Synchronized
    fun isStale(url: String): Boolean {
        val at = answerCache[url]?.at ?: return true
        return System.currentTimeMillis() - at > STALE_MS
    }

    @Synchronized
    fun putAnswers(url: String, data: AnswerCacheData) {
        val now = System.currentTimeMillis()
        answerCache[url] = Entry(data, now)
        writeAnswersToDisk(url, data, now)
    }

    /** 主动重试时清掉，强制重新抓 */
    @Synchronized
    fun invalidate(url: String) {
        answerCache.remove(url)
        runCatching { fileOf(url).delete() }
    }

    // ---------------- 评论 ----------------

    @Synchronized
    fun getComments(answerId: String): List<ZhihuComment>? = commentCache[answerId]?.data

    @Synchronized
    fun putComments(answerId: String, comments: List<ZhihuComment>) {
        commentCache[answerId] = Entry(comments, System.currentTimeMillis())
    }

    // ---------------- 磁盘读写 ----------------

    private fun fileOf(url: String): File =
        File(cacheDir, "a_${url.hashCode().toUInt()}.json")

    private fun writeAnswersToDisk(url: String, data: AnswerCacheData, at: Long) {
        try {
            val root = JSONObject()
            root["url"] = url
            root["at"] = at
            root["total"] = data.total
            root["hasMore"] = data.hasMore
            root["detail"] = blocksToJson(data.detailBlocks)
            root["answers"] = JSON.toJSON(data.answers.map { a ->
                JSONObject().apply {
                    this["id"] = a.id
                    this["author"] = a.author
                    this["headline"] = a.headline
                    this["vote"] = a.voteCount
                    this["comment"] = a.commentCount
                    this["blocks"] = blocksToJson(a.blocks)
                }
            })
            fileOf(url).writeText(root.toJSONString())
            trimDisk()
        } catch (e: Exception) {
            NLog.e(TAG, "write cache failed: $e")
        }
    }

    private fun readAnswersFromDisk(url: String): Entry<AnswerCacheData>? {
        val file = fileOf(url)
        if (!file.exists()) return null
        return try {
            val root = JSON.parseObject(file.readText()) ?: return null
            val answers = root.getJSONArray("answers")?.mapNotNull { item ->
                val o = item as? JSONObject ?: return@mapNotNull null
                ZhihuAnswer(
                    id = o.getString("id").orEmpty(),
                    author = o.getString("author").orEmpty(),
                    headline = o.getString("headline").orEmpty(),
                    voteCount = o.getIntValue("vote"),
                    commentCount = o.getIntValue("comment"),
                    blocks = jsonToBlocks(o.getJSONArray("blocks"))
                )
            }.orEmpty()
            if (answers.isEmpty()) return null
            Entry(
                AnswerCacheData(
                    detailBlocks = jsonToBlocks(root.getJSONArray("detail")),
                    answers = answers,
                    total = root.getIntValue("total"),
                    hasMore = root.getBooleanValue("hasMore")
                ),
                root.getLongValue("at")
            )
        } catch (e: Exception) {
            NLog.e(TAG, "read cache failed: $e")
            null
        }
    }

    private fun blocksToJson(blocks: List<ZhihuBlock>) = blocks.map { b ->
        JSONObject().apply {
            when (b) {
                is ZhihuBlock.TextBlock -> {
                    this["t"] = "text"
                    this["v"] = b.text
                }

                is ZhihuBlock.ImageBlock -> {
                    this["t"] = "image"
                    this["v"] = b.url
                }
            }
        }
    }

    private fun jsonToBlocks(array: com.alibaba.fastjson.JSONArray?): List<ZhihuBlock> {
        if (array == null) return emptyList()
        return array.mapNotNull { item ->
            val o = item as? JSONObject ?: return@mapNotNull null
            val v = o.getString("v").orEmpty()
            if (v.isEmpty()) return@mapNotNull null
            if (o.getString("t") == "image") ZhihuBlock.ImageBlock(v)
            else ZhihuBlock.TextBlock(v)
        }
    }

    /** 磁盘缓存文件过多时删掉最旧的 */
    private fun trimDisk() {
        try {
            val files = cacheDir.listFiles()?.filter { it.name.startsWith("a_") } ?: return
            if (files.size <= MAX_DISK_FILES) return
            files.sortedBy { it.lastModified() }
                .take(files.size - MAX_DISK_FILES)
                .forEach { it.delete() }
        } catch (e: Exception) {
            NLog.e(TAG, "trim cache failed: $e")
        }
    }
}

/** 缓存下来的一个问题的回答状态，包含分页进度 */
data class AnswerCacheData(
    val detailBlocks: List<ZhihuBlock>,
    val answers: List<ZhihuAnswer>,
    val total: Int,
    val hasMore: Boolean
)
