package gov.anzong.androidnga.activity.compose.zhihu.data

import java.io.Serializable

/**
 * 知乎热搜榜单里的一条。
 *
 * 正文（detail）随列表接口一起返回，所以详情页不需要再联网，
 * 直接把整个对象传过去渲染即可。
 */
data class ZhihuHotItem(
    /** 排名，从 1 开始 */
    val rank: Int,
    /** 标题 */
    val title: String,
    /** 摘要 */
    val excerpt: String,
    /** 热度文案，形如「1234 万热度」 */
    val detailText: String,
    /** 问题正文，可能为空 */
    val content: String,
    /** 回答数，取不到为 0 */
    val answerCount: Int,
    /** 关注数，取不到为 0 */
    val followerCount: Int,
    /** 热度数值 */
    val heat: Long,
    /** 热度趋势：up / down / normal */
    val trend: String,
    /** 对应知乎页面链接，可能为空 */
    val url: String
) : Serializable
