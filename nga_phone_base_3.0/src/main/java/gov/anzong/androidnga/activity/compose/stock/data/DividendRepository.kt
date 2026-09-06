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
     * 累加近12个月内已实施的分红。
     *
     * 用「各期现金总额之和 / 最新总股本」而不是简单累加每股派息——有回购或增发时
     * 各期股本不同，行情软件用的是前者，这样算出来的数才和它们对得上。
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

        val cutoff = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.YEAR, -1)
        }.timeInMillis
        var totalCash = 0.0
        var latestShares = 0.0
        for (i in 0 until rows.size) {
            val row = rows.getJSONObject(i) ?: continue
            if (row.getString("ASSIGN_PROGRESS") != PROGRESS_DONE) {
                continue
            }
            val exDate = row.getString("EX_DIVIDEND_DATE") ?: continue
            val exTime = parseDate(exDate)
            if (exTime < cutoff) {
                continue
            }
            // 已公告但除权日还没到的那期不能算——钱还没派，算进来会把股息率虚高一整期
            if (exTime > now) {
                continue
            }
            val shares = row.getDoubleValue("TOTAL_SHARES")
            if (shares <= 0.0) {
                continue
            }
            // 接口给的是每 10 股派息
            val perShare = row.getDoubleValue("PRETAX_BONUS_RMB") / 10.0
            totalCash += perShare * shares
            // 列表按除权日倒序，第一条即最新股本
            if (latestShares == 0.0) {
                latestShares = shares
            }
        }
        val perShare = if (latestShares > 0.0) totalCash / latestShares else 0.0
        return DividendInfo(code, perShare.toFloat(), now)
    }

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
