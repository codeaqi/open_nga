package gov.anzong.androidnga.activity.compose.zhihu.data

/**
 * 回答正文里的一段：要么是文字，要么是一张图。
 * 抽取时按原文顺序拆开，渲染时依次摆放，图文位置就和原帖一致。
 */
sealed class ZhihuBlock {
    data class TextBlock(val text: String) : ZhihuBlock()
    data class ImageBlock(val url: String) : ZhihuBlock()
}

/**
 * 一条回答下的评论。
 */
data class ZhihuComment(
    val author: String,
    val content: String,
    val likeCount: Int
)

/**
 * 一条知乎回答，已经从网页里抽成图文块，由 App 自己渲染。
 */
data class ZhihuAnswer(
    /** 回答 id，拉评论时要用 */
    val id: String,
    /** 作者昵称 */
    val author: String,
    /** 作者一句话简介，可能为空 */
    val headline: String,
    /** 赞同数 */
    val voteCount: Int,
    /** 评论数 */
    val commentCount: Int,
    /** 正文，按顺序排列的文字段和图片 */
    val blocks: List<ZhihuBlock>
)
