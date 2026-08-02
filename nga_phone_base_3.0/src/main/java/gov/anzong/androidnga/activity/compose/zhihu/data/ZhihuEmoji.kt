package gov.anzong.androidnga.activity.compose.zhihu.data

/**
 * 知乎表情映射。
 *
 * 知乎表情在 HTML 里是 `<img alt="[捂脸]">`，抓取时被换成了 alt 里的文字标记。
 * 这里把常见的几十个映射成对应 emoji，系统字体直接就能显示；
 * 没收录的保持 `[xxx]` 原样，至少不会丢内容。
 */
object ZhihuEmoji {

    private val MAP = mapOf(
        "捂脸" to "🤦",
        "笑哭" to "😂",
        "大笑" to "😄",
        "微笑" to "🙂",
        "害羞" to "☺️",
        "开心" to "😃",
        "耶" to "✌️",
        "赞" to "👍",
        "赞同" to "👍",
        "酷" to "😎",
        "拜托" to "🙏",
        "谢谢" to "🙏",
        "抱抱" to "🤗",
        "思考" to "🤔",
        "疑惑" to "🤨",
        "惊喜" to "😲",
        "惊讶" to "😮",
        "流泪" to "😢",
        "大哭" to "😭",
        "委屈" to "🥺",
        "生气" to "😠",
        "愤怒" to "😡",
        "无奈" to "😑",
        "白眼" to "🙄",
        "尴尬" to "😅",
        "汗" to "😓",
        "困" to "😪",
        "睡" to "😴",
        "亲亲" to "😘",
        "爱" to "❤️",
        "心碎" to "💔",
        "花" to "🌹",
        "礼物" to "🎁",
        "蛋糕" to "🎂",
        "撒花" to "🎉",
        "鼓掌" to "👏",
        "握手" to "🤝",
        "耐克嘲讽" to "🤡",
        "滑稽" to "🤡",
        "阴险" to "😏",
        "坏笑" to "😏",
        "吐" to "🤮",
        "衰" to "😩",
        "晕" to "😵",
        "口罩" to "😷",
        "墨镜" to "😎",
        "调皮" to "😜",
        "吐舌" to "😝",
        "闭嘴" to "🤐",
        "嘘" to "🤫",
        "机智" to "🤓",
        "皱眉" to "😦",
        "捂眼" to "🙈",
        "看" to "👀",
        "666" to "6️⃣",
        "好奇" to "🧐",
        "打脸" to "🤕",
        "发呆" to "😳",
        "可怜" to "🥺",
        "生病" to "🤒",
        "咒骂" to "🤬",
        "有帮助" to "👍"
    )

    private val REGEX = Regex("\\[([^\\[\\]]{1,8})]")

    /**
     * 把文本里的 `[捂脸]` 之类替换成 emoji。
     * 没收录的标记原样保留，避免把正文里本来就有的方括号内容吃掉。
     */
    fun replace(text: String): String {
        if (text.isEmpty() || !text.contains('[')) {
            return text
        }
        return REGEX.replace(text) { m ->
            MAP[m.groupValues[1]] ?: m.value
        }
    }
}
