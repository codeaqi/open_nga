package gov.anzong.androidnga.activity.compose.stock.data

/**
 * 一只股票的分红情况，按天缓存。
 *
 * 只缓存每股股息，不缓存股息率——股息率跟着股价每分钟变，
 * 存了就会过时，在界面上用现价现算即可。
 */
data class DividendInfo(
    val code: String,
    /** 近12个月每股税前现金股息 */
    val perShareDividend: Float,
    val updateTime: Long
) {

    /** 按给定股价算出的股息率（%），股价无效时返回 0 */
    fun yieldOf(price: Float): Float {
        if (price <= 0f) {
            return 0f
        }
        return perShareDividend / price * 100
    }
}
