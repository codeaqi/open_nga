package gov.anzong.androidnga.activity.compose.paper.data

import android.util.Xml
import gov.anzong.androidnga.base.util.ContextUtils
import gov.anzong.androidnga.common.util.NLog
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.StringReader
import java.util.concurrent.TimeUnit

/**
 * 论文数据源。
 *
 * 用 arXiv 官方 API（export.arxiv.org），返回 Atom XML，公开无需鉴权，
 * 也没有知乎那种反爬，所以直接用 OkHttp 请求即可，不需要 WebView。
 *
 * 论文清单是内置的，要加论文就在 [PAPER_IDS] 里追加 arXiv id。
 */
object PaperRepository {

    private const val TAG = "PaperRepository"

    /**
     * 精选论文清单，按阅读顺序排列。加论文就在这里追加 arXiv id
     * （形如 1706.03762，取自 arxiv.org/abs/ 后面那串）。
     */
    private val PAPER_IDS = listOf(
        "1706.03762"   // Attention Is All You Need —— Transformer 原论文
    )

    private const val API = "https://export.arxiv.org/api/query"

    /** 缓存有效期。论文元数据基本不变，存久一点 */
    private const val STALE_MS = 24 * 60 * 60 * 1000L

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    private var cachedList: List<PaperItem> = emptyList()

    @Volatile
    private var cachedAt = 0L

    private val cacheFile: File by lazy {
        File(ContextUtils.getContext().cacheDir, "paper").apply { mkdirs() }
            .let { File(it, "paper_list.xml") }
    }

    /**
     * 缓存的论文列表，内存没有就读磁盘。不判断过期——
     * 过期的也先返回，由调用方展示后再后台刷新。
     */
    @Synchronized
    fun getCachedList(): List<PaperItem> {
        if (cachedList.isNotEmpty()) return cachedList
        loadFromDisk()
        return cachedList
    }

    @Synchronized
    fun isStale(): Boolean = System.currentTimeMillis() - cachedAt > STALE_MS

    /**
     * 拉取论文列表。失败返回空列表，由调用方决定提示。
     */
    fun fetchPapers(): List<PaperItem> {
        if (PAPER_IDS.isEmpty()) return emptyList()
        val url = "$API?id_list=${PAPER_IDS.joinToString(",")}&max_results=${PAPER_IDS.size}"
        val xml = request(url) ?: return emptyList()
        val items = parse(xml)
        if (items.isNotEmpty()) {
            val now = System.currentTimeMillis()
            synchronized(this) {
                cachedList = items
                cachedAt = now
            }
            runCatching { cacheFile.writeText(xml) }
            NLog.e(TAG, "papers loaded: ${items.size}")
        }
        return items
    }

    private fun request(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "NgaClient/1.0 (Android)")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    NLog.e(TAG, "request failed: ${response.code}")
                    return null
                }
                response.body?.string()
            }
        } catch (e: Exception) {
            NLog.e(TAG, "request error: $e")
            null
        }
    }

    private fun loadFromDisk() {
        try {
            if (!cacheFile.exists()) return
            val items = parse(cacheFile.readText())
            if (items.isNotEmpty()) {
                cachedList = items
                cachedAt = cacheFile.lastModified()
            }
        } catch (e: Exception) {
            NLog.e(TAG, "load cache failed: $e")
        }
    }

    /**
     * 解析 arXiv 的 Atom XML。
     *
     * 每个 entry 里有 id、title、summary、published、author 下的 name、
     * category，以及 rel="related" 且 title="pdf" 的 link。
     * API 返回顺序不保证和请求顺序一致，所以最后按 [PAPER_IDS] 重排。
     */
    private fun parse(xml: String): List<PaperItem> {
        val result = mutableListOf<PaperItem>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xml))

            var inEntry = false
            var id = ""
            var title = ""
            var summary = ""
            var published = ""
            var category = ""
            var pdfUrl = ""
            var authors = mutableListOf<String>()
            var inAuthor = false

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "entry" -> {
                            inEntry = true
                            id = ""; title = ""; summary = ""; published = ""
                            category = ""; pdfUrl = ""
                            authors = mutableListOf()
                        }

                        "author" -> if (inEntry) inAuthor = true

                        "name" -> if (inEntry && inAuthor) {
                            parser.nextText().trim().takeIf { it.isNotEmpty() }
                                ?.let { authors.add(it) }
                        }

                        "id" -> if (inEntry && id.isEmpty()) {
                            id = parser.nextText().trim()
                        }

                        "title" -> if (inEntry && title.isEmpty()) {
                            title = parser.nextText().normalizeSpace()
                        }

                        "summary" -> if (inEntry) {
                            summary = parser.nextText().normalizeSpace()
                        }

                        "published" -> if (inEntry) {
                            published = parser.nextText().trim().take(10)
                        }

                        "category" -> if (inEntry && category.isEmpty()) {
                            category = parser.getAttributeValue(null, "term").orEmpty()
                        }

                        "link" -> if (inEntry) {
                            if (parser.getAttributeValue(null, "title") == "pdf") {
                                pdfUrl = parser.getAttributeValue(null, "href").orEmpty()
                            }
                        }
                    }

                    XmlPullParser.END_TAG -> when (parser.name) {
                        "author" -> inAuthor = false
                        "entry" -> {
                            inEntry = false
                            val shortId = id.substringAfter("/abs/")
                            if (title.isNotEmpty() && shortId.isNotEmpty()) {
                                result.add(
                                    PaperItem(
                                        arxivId = shortId,
                                        index = 0,
                                        title = title,
                                        authors = authors.toList(),
                                        summary = summary,
                                        published = published,
                                        category = category,
                                        absUrl = id.replace("http://", "https://"),
                                        pdfUrl = pdfUrl.ifEmpty {
                                            "https://arxiv.org/pdf/$shortId"
                                        }
                                    )
                                )
                            }
                        }
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            NLog.e(TAG, "parse failed: $e")
            return emptyList()
        }
        // 按内置清单的顺序排列并补上序号
        return PAPER_IDS.mapIndexedNotNull { i, wantId ->
            result.firstOrNull { it.arxivId.substringBefore("v") == wantId }
                ?.copy(index = i + 1)
        }
    }

    /** Atom 里的文本带换行和缩进，压成单行 */
    private fun String.normalizeSpace(): String =
        replace(Regex("\\s+"), " ").trim()
}
