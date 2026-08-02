package gov.anzong.androidnga.activity.compose.paper.data

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONArray
import gov.anzong.androidnga.common.util.NLog
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 英译中。
 *
 * 用 Google 翻译的公开端点（免 key）。一篇论文上百句，所以：
 * - 只翻当前屏幕看得到的句子，滚到哪翻到哪；
 * - 翻过的存内存缓存，滚回去不重复请求。
 */
object TranslateRepository {

    private const val TAG = "TranslateRepository"

    private const val ENDPOINT =
        "https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=zh-CN&dt=t&q="

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /** 原文 -> 译文。论文里重复句子不多，但滚动来回会反复取同一句 */
    private val cache = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, String>?
        ): Boolean = size > 800
    }

    @Synchronized
    fun getCached(text: String): String? = cache[text]

    /**
     * 翻译一句。失败返回 null，由调用方决定要不要重试。
     */
    fun translate(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        synchronized(this) { cache[trimmed] }?.let { return it }

        val url = ENDPOINT + URLEncoder.encode(trimmed, "UTF-8")
        val raw = try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    NLog.e(TAG, "translate failed: ${response.code}")
                    return null
                }
                response.body?.string()
            }
        } catch (e: Exception) {
            NLog.e(TAG, "translate error: $e")
            null
        } ?: return null

        val result = parse(raw) ?: return null
        synchronized(this) { cache[trimmed] = result }
        return result
    }

    /**
     * 返回结构是嵌套数组：[[[译文, 原文, ...], [译文2, 原文2, ...]], ...]
     * 长句会被拆成多段，要按顺序拼起来。
     */
    private fun parse(raw: String): String? {
        return try {
            val root = JSON.parseArray(raw) ?: return null
            val segments = root.getJSONArray(0) ?: return null
            val sb = StringBuilder()
            for (i in 0 until segments.size) {
                val seg = segments.get(i) as? JSONArray ?: continue
                val piece = seg.getString(0) ?: continue
                sb.append(piece)
            }
            sb.toString().trim().takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            NLog.e(TAG, "parse failed: $e")
            null
        }
    }

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
}
