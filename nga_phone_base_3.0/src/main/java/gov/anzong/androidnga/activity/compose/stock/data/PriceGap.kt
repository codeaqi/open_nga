package gov.anzong.androidnga.activity.compose.stock.data

/**
 * 现价到某档目标价之间的距离，两个数都是负的（还得往下跌）。
 *
 * @param percent 还需下跌的幅度（%）
 * @param diff 还需下跌的金额（元）
 */
data class PriceGap(val percent: Float, val diff: Float)

/**
 * 现价距目标价还差多少。
 *
 * 已经跌到位（含正好等于）时返回 null，由调用方显示「已到达」而不是画一个 0；
 * 价格无效（停牌拉不到行情、该档没设价）时同样返回 null。
 */
fun gapTo(currentPrice: Float, targetPrice: Float): PriceGap? {
    if (currentPrice <= 0f || targetPrice <= 0f || currentPrice <= targetPrice) {
        return null
    }
    return PriceGap(
        percent = (targetPrice - currentPrice) / currentPrice * 100f,
        diff = targetPrice - currentPrice
    )
}
