package gov.anzong.androidnga.activity.compose.paper.data

/**
 * 论文全文里的一块内容，按原文顺序排列，由 App 原生渲染。
 */
sealed class PaperBlock {

    /** 章节标题，level 2~6 对应层级 */
    data class Heading(val text: String, val level: Int) : PaperBlock()

    /**
     * 一个自然段，已按句子切开。每句单独一行并配一条中文翻译，
     * 所以段落不是整块文本，而是句子的集合。
     */
    data class Paragraph(val sentences: List<String>) : PaperBlock()

    /**
     * 插图（论文里的 Figure）。
     * 正文里只显示成「图 N」的链接，点开在单独页面看大图。
     */
    data class Image(val url: String, val index: Int) : PaperBlock()

    /**
     * 独立公式。渲染成图片显示，避免转纯文本后的乱码。
     * [latex] 是原始 LaTeX，用来生成图片地址和失败时兜底显示。
     */
    data class Formula(val latex: String) : PaperBlock()

    /** 表格，每行已用 ` | ` 拼好 */
    data class Table(val rows: List<String>) : PaperBlock()
}
