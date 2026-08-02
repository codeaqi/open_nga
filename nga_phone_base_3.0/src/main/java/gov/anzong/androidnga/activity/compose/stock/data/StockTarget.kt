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
    val showNote: Boolean = true,
    /** 是否由目标股息率自动推算三档价格 */
    val autoCalc: Boolean = false,
    /** 建仓时期望达到的股息率（%），仅在 [autoCalc] 打开时有意义 */
    val targetYield: Float = DEFAULT_TARGET_YIELD
) {

    /** 备注是否需要在列表里显示 */
    fun hasVisibleNote(): Boolean = showNote && note.isNotBlank()

    /** 三个价格都没设、没写备注、也没开自动计算时才算空，可以从存储里删掉 */
    fun isEmpty(): Boolean =
        buildPrice <= 0f && addPrice <= 0f && fullPrice <= 0f && note.isBlank() && !autoCalc

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

    companion object {

        /** 默认按 5% 股息率建仓 */
        const val DEFAULT_TARGET_YIELD = 5.0f

        /** 每加一档，价格再降 10% */
        private const val STEP_DOWN = 0.9f

        /**
         * 由每股股息和目标股息率推算三档价格。
         *
         * 建仓价来自股息率公式的反解：股息率 = 每股股息 / 股价，
         * 所以 建仓价 = 每股股息 / 目标股息率。
         * 加仓、满仓在此基础上each 再降 10%（股价越低股息率越高）。
         *
         * 没有分红数据或目标股息率非法时返回 null，由调用方保持手动值不变。
         */
        fun calculate(perShareDividend: Float, targetYield: Float): Triple<Float, Float, Float>? {
            if (perShareDividend <= 0f || targetYield <= 0f) {
                return null
            }
            val build = perShareDividend / (targetYield / 100f)
            val add = build * STEP_DOWN
            val full = add * STEP_DOWN
            return Triple(build, add, full)
        }
    }
}
