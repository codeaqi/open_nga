package gov.anzong.androidnga.activity.compose.stock.data

import java.util.Calendar

/**
 * A 股交易时段判断，用来决定要不要轮询行情。
 *
 * 只按周一到周五的 9:30-11:30、13:00-15:00 判断，不含法定节假日——
 * 节假日表得联网维护，而多轮询几次的代价远小于漏更新，所以这里从简。
 * 收盘后价格不再变化，轮询纯属浪费流量和电。
 */
object TradingHours {

    /** 上午 9:30 开盘，用「分钟数」比较，省去构造时间对象 */
    private const val MORNING_OPEN = 9 * 60 + 30

    private const val MORNING_CLOSE = 11 * 60 + 30

    private const val AFTERNOON_OPEN = 13 * 60

    private const val AFTERNOON_CLOSE = 15 * 60

    /**
     * 当前是否处于交易时段。
     * 集合竞价（9:15-9:30）也会有报价变化，但成交价要到 9:25 才有意义，
     * 这里统一从 9:30 起算。
     */
    fun isTrading(calendar: Calendar = Calendar.getInstance()): Boolean {
        val day = calendar.get(Calendar.DAY_OF_WEEK)
        if (day == Calendar.SATURDAY || day == Calendar.SUNDAY) {
            return false
        }
        val minutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        return (minutes in MORNING_OPEN..MORNING_CLOSE) ||
                (minutes in AFTERNOON_OPEN..AFTERNOON_CLOSE)
    }
}
