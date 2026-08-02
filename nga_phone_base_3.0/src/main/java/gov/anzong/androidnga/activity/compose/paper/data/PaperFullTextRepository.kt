package gov.anzong.androidnga.activity.compose.paper.data

import gov.anzong.androidnga.base.util.ContextUtils
import gov.anzong.androidnga.common.util.NLog
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 论文全文抓取。
 *
 * 从 ar5iv（arXiv 官方的 LaTeX→HTML 转换）拿 HTML，再抽成有序的图文块，
 * 由 App 原生渲染——不是丢给 WebView 显示网页。
 *
 * ar5iv 是纯静态页面，正文就在 HTML 里，普通 HTTP 请求即可，
 * 不像知乎那样需要 WebView 绕反爬。
 */
object PaperFullTextRepository {

    private const val TAG = "PaperFullText"

    /** 全文基本不变，缓存久一点 */
    private const val STALE_MS = 7 * 24 * 60 * 60 * 1000L

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val cacheDir: File by lazy {
        File(ContextUtils.getContext().cacheDir, "paper").apply { mkdirs() }
    }

    private fun cacheFile(arxivId: String) = File(cacheDir, "full_$arxivId.html")

    /**
     * 取全文。优先用未过期的本地缓存，没有再联网。
     * 返回空列表表示失败。
     */
    fun getFullText(arxivId: String): List<PaperBlock> {
        val file = cacheFile(arxivId)
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < STALE_MS) {
            val cached = runCatching { parse(file.readText(), arxivId) }.getOrNull()
            if (!cached.isNullOrEmpty()) {
                return cached
            }
        }
        val html = request("https://ar5iv.labs.arxiv.org/html/$arxivId") ?: return emptyList()
        val blocks = parse(html, arxivId)
        if (blocks.isNotEmpty()) {
            runCatching { file.writeText(html) }
            NLog.e(TAG, "full text loaded: $arxivId, ${blocks.size} blocks")
        }
        return blocks
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

    /**
     * 把 ar5iv 的 HTML 抽成有序图文块。
     *
     * 只挑正文相关的元素：章节标题（ltx_title）、段落（ltx_p）、
     * 插图和表格（ltx_tabular），按在文档里出现的先后顺序排列。
     */
    private fun parse(html: String, arxivId: String): List<PaperBlock> {
        val blocks = mutableListOf<PaperBlock>()
        var figureIndex = 0
        try {
            val matches = BLOCK_REGEX.findAll(html)
            for (m in matches) {
                val g = m.groupValues
                when {
                    // 章节标题
                    g[2].isNotEmpty() -> {
                        val text = htmlToText(g[2])
                        val level = g[1].toIntOrNull() ?: 2
                        if (text.isNotEmpty()) {
                            blocks.add(PaperBlock.Heading(text, level))
                        }
                    }
                    // 正文段落
                    g[3].isNotEmpty() -> {
                        val text = htmlToText(g[3])
                        if (text.length > 1) {
                            val sentences = splitSentences(text)
                            if (sentences.isNotEmpty()) {
                                blocks.add(PaperBlock.Paragraph(sentences))
                            }
                        }
                    }
                    // 插图：相对路径要补成绝对地址，并按出现顺序编号
                    g[4].isNotEmpty() -> {
                        val src = g[4]
                        // ar5iv 自己的站点 logo 和 base64 小图不是论文内容
                        if (!src.startsWith("data:") && !src.contains("/assets/ar5iv")) {
                            figureIndex++
                            blocks.add(
                                PaperBlock.Image(absoluteUrl(src, arxivId), figureIndex)
                            )
                        }
                    }
                    // 独立公式：取 alttext 里的 LaTeX，渲染成图片
                    g[5].isNotEmpty() -> {
                        val latex = MATH_WITH_ALT.find(g[5])
                            ?.groupValues?.get(1)
                            ?.let { decodeEntities(it).trim() }
                        if (!latex.isNullOrEmpty()) {
                            blocks.add(PaperBlock.Formula(latex))
                        }
                    }
                    // 表格
                    g[6].isNotEmpty() -> {
                        val rows = ROW_REGEX.findAll(g[6]).map { row ->
                            CELL_REGEX.findAll(row.groupValues[1])
                                .map { htmlToText(it.groupValues[1]) }
                                .joinToString(" | ")
                        }.filter { it.isNotBlank() && it.replace("|", "").isNotBlank() }
                            .toList()
                        if (rows.isNotEmpty()) {
                            blocks.add(PaperBlock.Table(rows))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            NLog.e(TAG, "parse failed: $e")
            return emptyList()
        }
        NLog.e(
            TAG, "parsed $arxivId: ${blocks.size} blocks, " +
                    "para=${blocks.count { it is PaperBlock.Paragraph }} " +
                    "sentence=${blocks.filterIsInstance<PaperBlock.Paragraph>()
                        .sumOf { it.sentences.size }} " +
                    "formula=${blocks.count { it is PaperBlock.Formula }} " +
                    "heading=${blocks.count { it is PaperBlock.Heading }} " +
                    "img=${blocks.count { it is PaperBlock.Image }}"
        )
        return blocks
    }

    /**
     * 把段落切成句子。每句要单独配一条翻译，所以按句末标点断开。
     * 缩写（e.g. / et al. / Fig.）后面的点不是句末，要避开。
     */
    private fun splitSentences(text: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            sb.append(c)
            if (c == '.' || c == '?' || c == '!') {
                val next = text.getOrNull(i + 1)
                // 句末标点后要跟空格或结尾，且不能是常见缩写
                if (next == null || next == ' ') {
                    val cur = sb.toString().trim()
                    if (!endsWithAbbrev(cur)) {
                        if (cur.length > 1) result.add(cur)
                        sb.clear()
                    }
                }
            }
            i++
        }
        val tail = sb.toString().trim()
        if (tail.length > 1) result.add(tail)
        return result
    }

    private fun endsWithAbbrev(s: String): Boolean {
        val lower = s.lowercase()
        return ABBREVS.any { lower.endsWith(it) }
    }

    private val ABBREVS = listOf(
        "e.g.", "i.e.", "et al.", "fig.", "eq.", "cf.", "vs.", "resp.",
        "approx.", "no.", "sec.", "ref.", "etc.", "dr.", "mr.", "st."
    )

    private fun absoluteUrl(src: String, arxivId: String): String = when {
        src.startsWith("http") -> src
        src.startsWith("//") -> "https:$src"
        src.startsWith("/") -> "https://ar5iv.labs.arxiv.org$src"
        else -> "https://ar5iv.labs.arxiv.org/html/$arxivId/$src"
    }

    /**
     * 标签转纯文本。公式取 MathML 的 alttext（原始 LaTeX）再转成可读形式，
     * 引用角标直接丢掉——正文里那些 [12] 编号在 App 里没有跳转目标。
     */
    private fun htmlToText(fragment: String): String {
        var t = fragment
        // 公式：用 alttext 里的 LaTeX。单个公式转换失败不该拖垮整篇，
        // 所以就地兜底成原始文本
        t = MATH_WITH_ALT.replace(t) {
            val raw = decodeEntities(it.groupValues[1])
            val converted = runCatching { latexToText(raw) }.getOrDefault(raw)
            " $converted "
        }
        t = MATH_ANY.replace(t, "")
        // 引用角标和参考文献链接
        t = SUP_REGEX.replace(t, "")
        t = CITE_REGEX.replace(t, "")
        t = TAG_REGEX.replace(t, "")
        return decodeEntities(t)
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex(" +([,.;:])"), "$1")
            .trim()
    }

    /** LaTeX 片段转成手机上能读的纯文本 */
    private fun latexToText(src: String): String {
        var t = src
        // 去掉只影响字体的命令，保留内容
        t = FONT_CMD.replace(t) { it.groupValues[2] }
        t = t.replace("\\left", "").replace("\\right", "")
        // 常见符号换成对应字符
        for ((k, v) in SYMBOLS) {
            t = t.replace(k, v)
        }
        t = SQRT_CMD.replace(t) { "√(${it.groupValues[1]})" }
        t = FRAC_CMD.replace(t) { "(${it.groupValues[1]})/(${it.groupValues[2]})" }
        // 上下标：短的不加括号更好读
        t = SUBSCRIPT.replace(t) { "_${it.groupValues[1]}" }
        t = SUPERSCRIPT.replace(t) { "^${it.groupValues[1]}" }
        // 剩下的命令和括号去掉
        t = ANY_CMD.replace(t, "")
        t = t.replace("{", "").replace("}", "")
        return t.replace(Regex("\\s+"), " ").trim()
    }

    private fun decodeEntities(s: String): String = s
        .replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'")
        .replace("&nbsp;", " ").replace("&#xA0;", " ")
        .replace("&#x2019;", "’").replace("&rsquo;", "’")
        .replace("&#x201C;", "“").replace("&ldquo;", "“")
        .replace("&#x201D;", "”").replace("&rdquo;", "”")
        .replace("&#x2013;", "–").replace("&ndash;", "–")
        .replace("&amp;", "&")

    private val SYMBOLS = listOf(
        "\\times" to "×", "\\cdot" to "·", "\\leq" to "≤", "\\geq" to "≥",
        "\\neq" to "≠", "\\approx" to "≈", "\\sum" to "Σ", "\\prod" to "Π",
        "\\infty" to "∞", "\\rightarrow" to "→", "\\alpha" to "α",
        "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ",
        "\\epsilon" to "ε", "\\theta" to "θ", "\\lambda" to "λ",
        "\\mu" to "μ", "\\sigma" to "σ", "\\pi" to "π", "\\ldots" to "…",
        "\\dots" to "…", "\\cdots" to "…", "\\in" to "∈", "\\sqrt" to "√"
    )

    /**
     * 注意顺序：ltx_equation 必须排在 ltx_tabular 之前。
     * 独立公式在 ar5iv 里也是 <table>（class="ltx_equation ltx_eqn_table"），
     * 先匹配表格的话公式会被当成表格，渲染出一堆乱码。
     */
    private val BLOCK_REGEX = Regex(
        "<h([2-6])[^>]*class=\"[^\"]*ltx_title[^\"]*\"[^>]*>([\\s\\S]*?)</h\\1>" +
                "|<p[^>]*class=\"[^\"]*ltx_p[^\"]*\"[^>]*>([\\s\\S]*?)</p>" +
                "|<img[^>]+src=\"([^\"]+)\"[^>]*>" +
                "|<table[^>]*class=\"[^\"]*ltx_equation[^\"]*\"[^>]*>([\\s\\S]*?)</table>" +
                "|<table[^>]*class=\"[^\"]*ltx_tabular[^\"]*\"[^>]*>([\\s\\S]*?)</table>"
    )

    // LaTeX 转换用的正则。注意 Android 的正则引擎要求右花括号也转义，
    // 写成裸的 } 会抛 PatternSyntaxException
    private val FONT_CMD =
        Regex("\\\\(mathbf|mathrm|text|mathcal|mathit|boldsymbol)\\{([^{}]*)\\}")
    private val SQRT_CMD = Regex("\\\\sqrt\\{([^{}]*)\\}")
    private val FRAC_CMD = Regex("\\\\frac\\{([^{}]*)\\}\\{([^{}]*)\\}")
    private val SUBSCRIPT = Regex("_\\{([^{}]{1,6})\\}")
    private val SUPERSCRIPT = Regex("\\^\\{([^{}]{1,6})\\}")
    private val ANY_CMD = Regex("\\\\[a-zA-Z]+")

    private val ROW_REGEX = Regex("<tr[^>]*>([\\s\\S]*?)</tr>")
    private val CELL_REGEX = Regex("<t[dh][^>]*>([\\s\\S]*?)</t[dh]>")
    private val MATH_WITH_ALT =
        Regex("<math[^>]*alttext=\"([^\"]*)\"[^>]*>[\\s\\S]*?</math>")
    private val MATH_ANY = Regex("<math[^>]*>[\\s\\S]*?</math>|<math[^>]*/>")
    private val SUP_REGEX = Regex("<sup[^>]*>[\\s\\S]*?</sup>")
    private val CITE_REGEX = Regex("<cite[^>]*>[\\s\\S]*?</cite>")
    private val TAG_REGEX = Regex("<[^>]+>")
}
