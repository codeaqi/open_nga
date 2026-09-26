package gov.anzong.androidnga.activity.compose.stock.data

import com.alibaba.fastjson.JSON
import gov.anzong.androidnga.base.util.PreferenceUtils
import gov.anzong.androidnga.common.util.NLog
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * 股息率数据源。分红明细来自东方财富数据中心的公开接口。
 *
 * 口径与行情软件一致：近12个月已实施的现金分红总额 / 最新总股本 / 最新价。
 * 分红一年才变一两次，所以每股股息按天缓存，只有股价变化时在本地重算股息率。
 */
object DividendRepository {

    private const val TAG = "DividendRepository"

    private const val KEY_DIVIDEND_CACHE = "stock_dividend_cache"

    /** 分红数据一天刷新一次足够 */
    private val CACHE_TTL = TimeUnit.DAYS.toMillis(1)

    /**
     * 注意：不能用 String.format 拼这个 URL——地址里的 %22（引号的 URL 编码）
     * 会被当成格式化占位符，抛 UnknownFormatConversionException。
     */
    private const val BONUS_URL_PREFIX =
        "https://datacenter-web.eastmoney.com/api/data/v1/get" +
                "?reportName=RPT_SHAREBONUS_DET&columns=ALL&pageSize=20" +
                "&sortColumns=EX_DIVIDEND_DATE&sortTypes=-1&filter=(SECURITY_CODE%3D%22"

    private const val BONUS_URL_SUFFIX = "%22)"

    /** 接口校验来源 */
    private const val REFERER = "https://data.eastmoney.com/"

    /** 只统计已实施的分红，预案和停止实施的不算 */
    private const val PROGRESS_DONE = "实施分配"

    /**
     * 最近一个派完的年度超过这个岁数就当它已经停止分红。
     *
     * 年报分红一般在次年年中除权，所以正常公司这个间隔不会超过一年；
     * 放到 18 个月是给派息偏晚的公司留余量。
     */
    private val STALE_AFTER_MS = TimeUnit.DAYS.toMillis(548)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /** 内存缓存，避免同一次会话反复读写 SharedPreferences */
    private val memoryCache: MutableMap<String, DividendInfo> by lazy {
        loadCache().toMutableMap()
    }

    /**
     * 取每股股息（税前，近12个月）。缓存未过期直接返回，过期或没有则返回 null，
     * 由调用方决定是否发起网络请求。
     */
    fun getCached(code: String): DividendInfo? {
        val info = memoryCache[code] ?: return null
        if (System.currentTimeMillis() - info.updateTime > CACHE_TTL) {
            return null
        }
        return info
    }

    /**
     * 请求并缓存某只股票的每股股息。网络失败时返回 null，不写缓存——
     * 避免把一次网络抖动固化成"该股票不分红"。
     */
    fun fetchDividend(code: String): DividendInfo? {
        return try {
            val simpleCode = if (code.length > 2) code.substring(2) else code
            val request = Request.Builder()
                .url(BONUS_URL_PREFIX + simpleCode + BONUS_URL_SUFFIX)
                .header("Referer", REFERER)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    NLog.e(TAG, "fetch dividend failed: ${response.code}")
                    return null
                }
                val body = response.body?.string() ?: return null
                val info = parse(code, body) ?: return null
                memoryCache[code] = info
                saveCache(memoryCache)
                info
            }
        } catch (e: Exception) {
            NLog.e(TAG, "fetch dividend error: $e")
            null
        }
    }

    /**
     * 算出每股股息。
     *
     * 口径是**最近一个已经派完的会计年度**，而不是近12个月的滚动窗口。滚动窗口在
     * 一年多派的股票上会错位：中国移动 6 月、9 月各派一次，2026-09-06 这天窗口起点
     * 刚越过 2025-09-01 那期，而当年 9 月那期还没除权，窗口里只剩一期，股息率直接
     * 腰斩成 2.19%。按年度汇总就不会随日子跳。
     *
     * 判定「派完」的标志是该年度的年报（REPORT_DATE 为 12 月）那期已经除权。
     * 没有任何年报分红的公司（只发中期、或刚上市）退回近12个月口径。
     *
     * [now] 只为测试可注入，正常调用取当前时间。
     */
    internal fun parse(
        code: String,
        body: String,
        now: Long = System.currentTimeMillis()
    ): DividendInfo? {
        val root = JSON.parseObject(body) ?: return null
        // 从不分红的股票 result 直接是 null，这是正常情况，记为 0
        val result = root.getJSONObject("result")
            ?: return DividendInfo(code, 0f, now)
        val rows = result.getJSONArray("data")
            ?: return DividendInfo(code, 0f, now)

        val payouts = ArrayList<Payout>()
        for (i in 0 until rows.size) {
            val row = rows.getJSONObject(i) ?: continue
            if (row.getString("ASSIGN_PROGRESS") != PROGRESS_DONE) {
                continue
            }
            val exTime = parseDate(row.getString("EX_DIVIDEND_DATE") ?: continue)
            // 已公告但除权日还没到的那期不能算——钱还没派，算进来会把股息率虚高一整期
            if (exTime == 0L || exTime > now) {
                continue
            }
            val shares = row.getDoubleValue("TOTAL_SHARES")
            if (shares <= 0.0) {
                continue
            }
            val report = row.getString("REPORT_DATE") ?: continue
            val year = report.substring(0, 4).toIntOrNull() ?: continue
            val month = report.substring(5, 7).toIntOrNull() ?: continue
            // 接口给的是每 10 股派息
            val perShare = row.getDoubleValue("PRETAX_BONUS_RMB") / 10.0
            payouts.add(Payout(exTime, year, month, perShare, shares))
        }

        val perShare = latestCompleteFiscalYear(payouts, now) ?: trailingTwelveMonths(payouts, now)
        return DividendInfo(code, perShare.toFloat(), now)
    }

    /**
     * 最近一个已派完的会计年度的每股股息，没有这样的年度时返回 null。
     *
     * 年度太久远说明这家公司早就不分红了，拿陈年数据充数会让人以为还有高息，
     * 这种情况一并返回 null，交给近12个月口径算出 0。
     */
    private fun latestCompleteFiscalYear(payouts: List<Payout>, now: Long): Double? {
        val byYear = payouts.groupBy { it.reportYear }
        val year = byYear.entries
            .filter { entry -> entry.value.any { it.reportMonth == 12 } }
            .maxOfOrNull { it.key } ?: return null
        val yearPayouts = byYear.getValue(year)
        if (now - yearPayouts.maxOf { it.exTime } > STALE_AFTER_MS) {
            return null
        }
        return weightedPerShare(yearPayouts)
    }

    /** 兜底口径：近12个月内已除权的分红 */
    private fun trailingTwelveMonths(payouts: List<Payout>, now: Long): Double {
        val cutoff = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.YEAR, -1)
        }.timeInMillis
        return weightedPerShare(payouts.filter { it.exTime >= cutoff })
    }

    /**
     * 用「各期现金总额之和 / 最新总股本」而不是简单累加每股派息——有回购或增发时
     * 各期股本不同，行情软件用的是前者，这样算出来的数才和它们对得上。
     */
    private fun weightedPerShare(payouts: List<Payout>): Double {
        if (payouts.isEmpty()) {
            return 0.0
        }
        val sorted = payouts.sortedByDescending { it.exTime }
        val totalCash = sorted.sumOf { it.perShare * it.shares }
        // 最新一期的股本即当前股本
        return totalCash / sorted.first().shares
    }

    /** 已实施的一期分红 */
    private data class Payout(
        val exTime: Long,
        /** 分红对应的报告期年份，12 月的那期是年报 */
        val reportYear: Int,
        val reportMonth: Int,
        val perShare: Double,
        val shares: Double
    )

    /** 日期形如 2026-06-26 00:00:00，只取日期部分 */
    private fun parseDate(text: String): Long {
        return try {
            val date = text.substring(0, 10).split("-")
            Calendar.getInstance().apply {
                set(date[0].toInt(), date[1].toInt() - 1, date[2].toInt(), 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } catch (e: Exception) {
            0L
        }
    }

    /** 缓存格式：代码:每股股息:更新时间，条目间用逗号分隔 */
    private fun loadCache(): Map<String, DividendInfo> {
        val saved = PreferenceUtils.getData(KEY_DIVIDEND_CACHE, "")
        if (saved.isNullOrEmpty()) {
            return emptyMap()
        }
        val result = mutableMapOf<String, DividendInfo>()
        saved.split(",").forEach { entry ->
            val parts = entry.split(":")
            if (parts.size != 3) {
                return@forEach
            }
            val perShare = parts[1].toFloatOrNull() ?: return@forEach
            val time = parts[2].toLongOrNull() ?: return@forEach
            result[parts[0]] = DividendInfo(parts[0], perShare, time)
        }
        return result
    }

    private fun saveCache(cache: Map<String, DividendInfo>) {
        val text = cache.values.joinToString(",") {
            "${it.code}:${it.perShareDividend}:${it.updateTime}"
        }
        PreferenceUtils.putData(KEY_DIVIDEND_CACHE, text)
    }
}
