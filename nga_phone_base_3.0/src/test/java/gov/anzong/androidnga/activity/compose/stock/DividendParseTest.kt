package gov.anzong.androidnga.activity.compose.stock

import gov.anzong.androidnga.activity.compose.stock.data.DividendRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class DividendParseTest {

    /**
     * 中国移动一年派两次（6月、9月）。2026-09-06 这天，滚动12个月窗口的起点刚好
     * 越过 2025-09-01 那期，而 2026 年 9 月那期还没除权——窗口里只剩一期，
     * 算出来 2.2012（2.19%），实际该是一整年的 4.6961（4.68%）。
     */
    @Test
    fun `一年两派的股票不会因为窗口错位只算到一期`() {
        val info = DividendRepository.parse("sh600941", CHINA_MOBILE_JSON, at(2026, 9, 6))!!
        assertEquals(4.6961f, info.perShareDividend, 0.001f)
    }

    /** 平安同样按年度汇总：2025 年度＝中期 0.95 + 末期 1.75 */
    @Test
    fun `按最近一个派完的会计年度汇总`() {
        val info = DividendRepository.parse("sh601318", PING_AN_JSON, at(2026, 9, 6))!!
        assertEquals(2.70f, info.perShareDividend, 0.001f)
    }

    /**
     * 2026-09-10 那期属于 2026 年度中期，2026 年度还没派完，
     * 所以除权之后结果也不该变——它要等 2026 年报那期一起算。
     */
    @Test
    fun `新一年的中期分红不会混进上一年度`() {
        val info = DividendRepository.parse("sh601318", PING_AN_JSON, at(2026, 9, 11))!!
        assertEquals(2.70f, info.perShareDividend, 0.001f)
    }

    /** 年报那期还没除权时，最近派完的是上一年度 */
    @Test
    fun `年报分红还没除权就用上一年度`() {
        // 2026-06-01：2026-06-10 那期（2025年报）还没到，2025年度不算派完
        val info = DividendRepository.parse("sh601318", PING_AN_JSON, at(2026, 6, 1))!!
        // 2024 年度＝中期 0.93 + 末期 1.62
        assertEquals(2.55f, info.perShareDividend, 0.001f)
    }

    /** 没有任何年报分红的公司退回近12个月口径，不能直接算 0 */
    @Test
    fun `只发中期分红的公司回退到近12个月`() {
        val json = """
{"result":{"data":[
{"EX_DIVIDEND_DATE":"2026-03-10 00:00:00","REPORT_DATE":"2025-06-30 00:00:00","ASSIGN_PROGRESS":"实施分配","PRETAX_BONUS_RMB":5.0,"TOTAL_SHARES":1000000000}
]}}"""
        val info = DividendRepository.parse("sh000001", json, at(2026, 9, 6))!!
        assertEquals(0.5f, info.perShareDividend, 0.001f)
    }

    /** 早就不分红的公司不能拿好几年前的年度充数 */
    @Test
    fun `停止分红多年的公司算作没有股息`() {
        val json = """
{"result":{"data":[
{"EX_DIVIDEND_DATE":"2021-06-10 00:00:00","REPORT_DATE":"2020-12-31 00:00:00","ASSIGN_PROGRESS":"实施分配","PRETAX_BONUS_RMB":8.0,"TOTAL_SHARES":1000000000}
]}}"""
        val info = DividendRepository.parse("sh000002", json, at(2026, 9, 6))!!
        assertEquals(0f, info.perShareDividend, 0.001f)
    }

    /** 预案、停止实施的不算 */
    @Test
    fun `没实施的分红不计入`() {
        val json = """
{"result":{"data":[
{"EX_DIVIDEND_DATE":"2026-06-10 00:00:00","REPORT_DATE":"2025-12-31 00:00:00","ASSIGN_PROGRESS":"董事会预案","PRETAX_BONUS_RMB":10.0,"TOTAL_SHARES":1000000000}
]}}"""
        val info = DividendRepository.parse("sh000003", json, at(2026, 9, 6))!!
        assertEquals(0f, info.perShareDividend, 0.001f)
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
{"EX_DIVIDEND_DATE":"2026-09-10 00:00:00","REPORT_DATE":"2026-06-30 00:00:00","ASSIGN_PROGRESS":"实施分配","PRETAX_BONUS_RMB":9.8,"TOTAL_SHARES":18107641995},
{"EX_DIVIDEND_DATE":"2026-06-10 00:00:00","REPORT_DATE":"2025-12-31 00:00:00","ASSIGN_PROGRESS":"实施分配","PRETAX_BONUS_RMB":17.5,"TOTAL_SHARES":18107641995},
{"EX_DIVIDEND_DATE":"2025-10-24 00:00:00","REPORT_DATE":"2025-06-30 00:00:00","ASSIGN_PROGRESS":"实施分配","PRETAX_BONUS_RMB":9.5,"TOTAL_SHARES":18107641995},
{"EX_DIVIDEND_DATE":"2025-06-30 00:00:00","REPORT_DATE":"2024-12-31 00:00:00","ASSIGN_PROGRESS":"实施分配","PRETAX_BONUS_RMB":16.2,"TOTAL_SHARES":18210234607},
{"EX_DIVIDEND_DATE":"2024-10-18 00:00:00","REPORT_DATE":"2024-06-30 00:00:00","ASSIGN_PROGRESS":"实施分配","PRETAX_BONUS_RMB":9.3,"TOTAL_SHARES":18210234607},
{"EX_DIVIDEND_DATE":"2024-07-26 00:00:00","REPORT_DATE":"2023-12-31 00:00:00","ASSIGN_PROGRESS":"实施分配","PRETAX_BONUS_RMB":15,"TOTAL_SHARES":18210234607}
]}}"""

        private const val CHINA_MOBILE_JSON = """
{"result":{"data":[
{"EX_DIVIDEND_DATE":"2026-06-05 00:00:00","REPORT_DATE":"2025-12-31 00:00:00","ASSIGN_PROGRESS":"实施分配","PRETAX_BONUS_RMB":22.012,"TOTAL_SHARES":21679414843},
{"EX_DIVIDEND_DATE":"2025-09-01 00:00:00","REPORT_DATE":"2025-06-30 00:00:00","ASSIGN_PROGRESS":"实施分配","PRETAX_BONUS_RMB":25.025,"TOTAL_SHARES":21613354979},
{"EX_DIVIDEND_DATE":"2025-06-06 00:00:00","REPORT_DATE":"2024-12-31 00:00:00","ASSIGN_PROGRESS":"实施分配","PRETAX_BONUS_RMB":22.916,"TOTAL_SHARES":21606238818},
{"EX_DIVIDEND_DATE":"2024-09-02 00:00:00","REPORT_DATE":"2024-06-30 00:00:00","ASSIGN_PROGRESS":"实施分配","PRETAX_BONUS_RMB":23.789,"TOTAL_SHARES":21449841521},
{"EX_DIVIDEND_DATE":"2024-06-06 00:00:00","REPORT_DATE":"2023-12-31 00:00:00","ASSIGN_PROGRESS":"实施分配","PRETAX_BONUS_RMB":21.849,"TOTAL_SHARES":21427608756}
]}}"""
    }
}
