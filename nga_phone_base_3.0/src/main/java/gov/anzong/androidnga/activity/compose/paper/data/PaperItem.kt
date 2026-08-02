package gov.anzong.androidnga.activity.compose.paper.data

import java.io.Serializable

/**
 * 一篇 arXiv 论文。元数据来自 arXiv 官方 API。
 */
data class PaperItem(
    /** arXiv id，形如 1706.03762 */
    val arxivId: String,
    /** 序号，从 1 开始 */
    val index: Int,
    val title: String,
    /** 作者列表 */
    val authors: List<String>,
    /** 摘要 */
    val summary: String,
    /** 首次发布日期，形如 2017-06-12 */
    val published: String,
    /** 主分类，如 cs.CL */
    val category: String,
    /** 网页地址 */
    val absUrl: String,
    /** PDF 地址 */
    val pdfUrl: String
) : Serializable {

    /** 作者太多时列表页只显示前几个 */
    fun authorSummary(max: Int = 3): String {
        if (authors.isEmpty()) return ""
        return if (authors.size <= max) {
            authors.joinToString("、")
        } else {
            authors.take(max).joinToString("、") + " 等 ${authors.size} 人"
        }
    }
}
