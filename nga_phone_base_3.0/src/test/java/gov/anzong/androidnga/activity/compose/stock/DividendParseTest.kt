package gov.anzong.androidnga.activity.compose.stock

import gov.anzong.androidnga.activity.compose.stock.data.DividendRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class DividendParseTest {

    @Test
    fun `除权日还没到的分红不计入近12个月`() {
        // 中国平安 2026-09-06 的真实数据：2026-09-10 那期已公告但还没除权
        val info = DividendRepository.parse("sh601318", PING_AN_JSON, at(2026, 9, 6))!!
        // 只该算 2026-06-10 的 1.75 和 2025-10-24 的 0.95
        assertEquals(2.70f, info.perShareDividend, 0.001f)
    }

    @Test
    fun `除权日已过的分红照常计入`() {
        // 挪到 2026-09-11，那期除权完了就该算进来
        val info = DividendRepository.parse("sh601318", PING_AN_JSON, at(2026, 9, 11))!!
        assertEquals(3.68f, info.perShareDividend, 0.001f)
    }

    @Test
    fun `超过12个月的分红被排除`() {
        // 2026-10-25：2025-10-24 那期滚出窗口，只剩 2026-06-10 和 2026-09-10
        val info = DividendRepository.parse("sh601318", PING_AN_JSON, at(2026, 10, 25))!!
        assertEquals(2.73f, info.perShareDividend, 0.001f)
    }

    private fun at(year: Int, month: Int, day: Int): Long {
        return Calendar.getInstance().apply {
            set(year, month - 1, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    companion object {
        /** 东方财富 RPT_SHAREBONUS_DET 的真实返回，只留用到的字段 */
        private const val PING_AN_JSON = """
{"result":{"data":[
{"EX_DIVIDEND_DATE":"2026-09-10 00:00:00","ASSIGN_PROGRESS":"实施分配","PRETAX_BONUS_RMB":9.8,"TOTAL_SHARES":18107641995},
{"EX_DIVIDEND_DATE":"2026-06-10 00:00:00","ASSIGN_PROGRESS":"实施分配","PRETAX_BONUS_RMB":17.5,"TOTAL_SHARES":18107641995},
{"EX_DIVIDEND_DATE":"2025-10-24 00:00:00","ASSIGN_PROGRESS":"实施分配","PRETAX_BONUS_RMB":9.5,"TOTAL_SHARES":18107641995},
{"EX_DIVIDEND_DATE":"2025-06-30 00:00:00","ASSIGN_PROGRESS":"实施分配","PRETAX_BONUS_RMB":16.2,"TOTAL_SHARES":18210234607},
{"EX_DIVIDEND_DATE":"2024-10-18 00:00:00","ASSIGN_PROGRESS":"实施分配","PRETAX_BONUS_RMB":9.3,"TOTAL_SHARES":18210234607}
]}}"""
    }
}
