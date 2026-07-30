package gov.anzong.androidnga.activity.compose.stock.data

/**
 * 单只股票的建仓目标价。价格为 0 表示未设置。
 */
data class StockTarget(
    /** 建仓价，对应 20% 仓位 */
    val buildPrice: Float = 0f,
    /** 加仓价，对应 30% 仓位 */
    val addPrice: Float = 0f,
    /** 满仓价，对应 50% 仓位 */
    val fullPrice: Float = 0f,
    /** 自己写的备注 */
    val note: String = "",
    /** 备注是否显示在列表里，关掉后仍然保留内容，只是不占列表的行 */
    val showNote: Boolean = true
) {

    /** 备注是否需要在列表里显示 */
    fun hasVisibleNote(): Boolean = showNote && note.isNotBlank()

    /** 三个价格都没设、也没写备注时才算空，可以从存储里删掉 */
    fun isEmpty(): Boolean =
        buildPrice <= 0f && addPrice <= 0f && fullPrice <= 0f && note.isBlank()

    /** 只判断价格，用于决定要不要画那排价格标签 */
    fun hasNoPrice(): Boolean = buildPrice <= 0f && addPrice <= 0f && fullPrice <= 0f

    /**
     * 当前价已触及的最深档位，用于列表高亮。
     * 价格越低仓位越重，因此从满仓档开始判断。
     */
    fun reachedLevel(price: Float): Level? = when {
        fullPrice > 0f && price <= fullPrice -> Level.FULL
        addPrice > 0f && price <= addPrice -> Level.ADD
        buildPrice > 0f && price <= buildPrice -> Level.BUILD
        else -> null
    }

    enum class Level(val label: String, val percent: Int) {
        BUILD("建仓", 20),
        ADD("加仓", 30),
        FULL("满仓", 50)
    }
}
