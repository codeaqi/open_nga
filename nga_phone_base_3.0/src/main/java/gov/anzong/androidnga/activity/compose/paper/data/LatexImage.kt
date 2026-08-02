package gov.anzong.androidnga.activity.compose.paper.data

import gov.anzong.androidnga.common.util.NLog
import java.net.URLEncoder

/**
 * LaTeX 公式转图片。
 *
 * 公式转成纯文本必然丢信息（上下标、分式、求和号会挤成一行），
 * 所以交给在线渲染服务出 PNG，效果和 PDF 里一致。
 * 图片由 Coil 负责下载和磁盘缓存，同一个公式只会拉一次。
 */
object LatexImage {

    private const val ENDPOINT = "https://latex.codecogs.com/png.image?"

    /** 独立公式用大一点的 dpi，行内公式小一些 */
    private const val DPI_BLOCK = 220
    private const val DPI_INLINE = 160

    fun blockUrl(latex: String): String = build(latex, DPI_BLOCK)

    fun inlineUrl(latex: String): String = build(latex, DPI_INLINE)

    private fun build(latex: String, dpi: Int): String {
        // \dpi{n} 控制清晰度，\bg{white} 不加——留透明背景才能跟着主题走
        val expr = "\\dpi{$dpi}${latex.trim()}"
        val url = ENDPOINT + URLEncoder.encode(expr, "UTF-8")
            // URLEncoder 把空格编码成 +，但这里要的是 %20
            .replace("+", "%20")
        NLog.e(TAG, "formula url: $url")
        return url
    }

    private const val TAG = "LatexImage"
}
