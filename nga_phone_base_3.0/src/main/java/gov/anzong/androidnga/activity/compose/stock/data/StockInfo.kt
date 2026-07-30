package gov.anzong.androidnga.activity.compose.stock.data

/**
 * 单只股票的行情快照
 */
data class StockInfo(
    /** 带市场前缀的代码，如 sh601398 */
    val code: String,
    val name: String,
    /** 当前价，停牌或未开盘时取昨收 */
    val price: Float,
    /** 昨收价 */
    val preClose: Float,
    /** 行情时间，形如 2026-07-27 15:00:00 */
    val time: String
) {

    /** 涨跌额 */
    val change: Float
        get() = price - preClose

    /** 涨跌幅，单位百分比 */
    val changePercent: Float
        get() = if (preClose == 0f) 0f else change / preClose * 100

    /** 不带市场前缀的代码，用于展示 */
    val simpleCode: String
        get() = if (code.length > 2) code.substring(2) else code
}
