package gov.anzong.androidnga.activity.compose.stock.data

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import gov.anzong.androidnga.base.util.PreferenceUtils
import gov.anzong.androidnga.common.util.NLog
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 自选股数据源。行情来自新浪财经的公开接口，与 NGA 的网络栈相互独立，
 * 避免把论坛的 Cookie、UA 带到第三方服务上。
 */
object StockRepository {

    private const val TAG = "StockRepository"

    private const val KEY_STOCK_LIST = "stock_watch_list"

    /** 旧的紧凑格式，只用于一次性迁移 */
    private const val KEY_STOCK_TARGET = "stock_target_price"

    private const val KEY_STOCK_TARGET_JSON = "stock_target_price_json"

    private const val QUOTE_URL = "https://hq.sinajs.cn/list="

    /** 接口校验来源，缺少该头会被拒绝 */
    private const val REFERER = "https://finance.sina.com.cn"

    /** 返回体是 GBK 编码，需按此解码否则股票名称乱码 */
    private const val CHARSET_GBK = "GBK"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 读取自选股代码列表，形如 [sh601398, sh601919]
     */
    fun loadWatchList(): List<String> {
        val saved = PreferenceUtils.getData(KEY_STOCK_LIST, "")
        if (saved.isNullOrEmpty()) {
            return emptyList()
        }
        return saved.split(",").filter { it.isNotBlank() }
    }

    fun saveWatchList(codes: List<String>) {
        PreferenceUtils.putData(KEY_STOCK_LIST, codes.joinToString(","))
    }

    fun addStock(code: String): List<String> {
        val normalized = normalizeCode(code) ?: return loadWatchList()
        val list = loadWatchList().toMutableList()
        if (!list.contains(normalized)) {
            list.add(normalized)
            saveWatchList(list)
        }
        return list
    }

    fun removeStock(code: String): List<String> {
        val list = loadWatchList().toMutableList()
        list.remove(code)
        saveWatchList(list)
        removeTarget(code)
        return list
    }

    /**
     * 把某只股票在自选列表里上移或下移一位。
     * 已经在头/尾时原样返回，调用方据此判断按钮是否可用。
     */
    fun moveStock(code: String, up: Boolean): List<String> {
        val list = loadWatchList().toMutableList()
        val index = list.indexOf(code)
        if (index < 0) {
            return list
        }
        val target = if (up) index - 1 else index + 1
        if (target < 0 || target >= list.size) {
            return list
        }
        list[index] = list[target]
        list[target] = code
        saveWatchList(list)
        return list
    }

    /**
     * 目标价与备注用 JSON 存储。
     *
     * 原先是 "代码:建仓|加仓|满仓" 的紧凑格式，但备注是自由文本，里面的逗号和冒号
     * 会把分隔符冲掉，所以改用 JSON。[migrateLegacyTargets] 负责读旧格式。
     */
    fun loadTargets(): Map<String, StockTarget> {
        val saved = PreferenceUtils.getData(KEY_STOCK_TARGET_JSON, "")
        if (!saved.isNullOrEmpty()) {
            return try {
                val map = JSON.parseObject(saved)
                val result = mutableMapOf<String, StockTarget>()
                map?.keys?.forEach { code ->
                    val obj = map.getJSONObject(code) ?: return@forEach
                    result[code] = StockTarget(
                        buildPrice = obj.getFloatValue("buildPrice"),
                        addPrice = obj.getFloatValue("addPrice"),
                        fullPrice = obj.getFloatValue("fullPrice"),
                        note = obj.getString("note") ?: "",
                        // 老数据没有这个字段，默认显示
                        showNote = obj.getBooleanValue("showNote") || !obj.containsKey("showNote")
                    )
                }
                result
            } catch (e: Exception) {
                NLog.e(TAG, "loadTargets failed: $e")
                emptyMap()
            }
        }
        return migrateLegacyTargets()
    }

    /** 读一次旧的紧凑格式并转存为 JSON，之后就不再走这条路 */
    private fun migrateLegacyTargets(): Map<String, StockTarget> {
        val legacy = PreferenceUtils.getData(KEY_STOCK_TARGET, "")
        if (legacy.isNullOrEmpty()) {
            return emptyMap()
        }
        val result = mutableMapOf<String, StockTarget>()
        legacy.split(",").forEach { entry ->
            val parts = entry.split(":")
            if (parts.size != 2) {
                return@forEach
            }
            val prices = parts[1].split("|")
            if (prices.size != 3) {
                return@forEach
            }
            result[parts[0]] = StockTarget(
                buildPrice = prices[0].toFloatOrNull() ?: 0f,
                addPrice = prices[1].toFloatOrNull() ?: 0f,
                fullPrice = prices[2].toFloatOrNull() ?: 0f
            )
        }
        if (result.isNotEmpty()) {
            saveTargets(result)
        }
        return result
    }

    fun loadTarget(code: String): StockTarget = loadTargets()[code] ?: StockTarget()

    fun saveTarget(code: String, target: StockTarget) {
        val targets = loadTargets().toMutableMap()
        if (target.isEmpty()) {
            targets.remove(code)
        } else {
            targets[code] = target
        }
        saveTargets(targets)
    }

    private fun removeTarget(code: String) {
        val targets = loadTargets().toMutableMap()
        if (targets.remove(code) != null) {
            saveTargets(targets)
        }
    }

    private fun saveTargets(targets: Map<String, StockTarget>) {
        val root = JSONObject()
        targets.forEach { (code, target) ->
            root[code] = JSONObject().apply {
                this["buildPrice"] = target.buildPrice
                this["addPrice"] = target.addPrice
                this["fullPrice"] = target.fullPrice
                this["note"] = target.note
                this["showNote"] = target.showNote
            }
        }
        PreferenceUtils.putData(KEY_STOCK_TARGET_JSON, root.toJSONString())
    }

    /**
     * 把用户输入的 6 位代码补全为带市场前缀的形式。
     * 6 开头为沪市，0/3 开头为深市，8/4 开头为北交所。
     */
    fun normalizeCode(input: String): String? {
        val code = input.trim().lowercase()
        if (code.startsWith("sh") || code.startsWith("sz") || code.startsWith("bj")) {
            return if (code.length == 8) code else null
        }
        if (code.length != 6 || !code.all { it.isDigit() }) {
            return null
        }
        return when (code.first()) {
            '6' -> "sh$code"
            '0', '3' -> "sz$code"
            '8', '4' -> "bj$code"
            else -> null
        }
    }

    /**
     * 批量拉取行情，一次请求可查多只。失败返回空列表，由调用方决定如何提示。
     */
    fun fetchQuotes(codes: List<String>): List<StockInfo> {
        if (codes.isEmpty()) {
            return emptyList()
        }
        val request = Request.Builder()
            .url(QUOTE_URL + codes.joinToString(","))
            .header("Referer", REFERER)
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    NLog.e(TAG, "fetch quotes failed: ${response.code}")
                    return emptyList()
                }
                val body = response.body?.bytes() ?: return emptyList()
                parse(String(body, charset(CHARSET_GBK)))
            }
        } catch (e: Exception) {
            NLog.e(TAG, "fetch quotes error: $e")
            emptyList()
        }
    }

    /**
     * 解析形如 var hq_str_sh601398="工商银行,7.790,7.750,7.730,..."; 的响应，
     * 每行一只股票，字段以逗号分隔。
     */
    private fun parse(raw: String): List<StockInfo> {
        val result = mutableListOf<StockInfo>()
        raw.lines().forEach { line ->
            val code = line.substringAfter("hq_str_", "").substringBefore("=", "")
            val content = line.substringAfter("\"", "").substringBeforeLast("\"", "")
            if (code.isEmpty() || content.isEmpty()) {
                return@forEach
            }
            val fields = content.split(",")
            // 至少要能取到名称、昨收、现价和时间
            if (fields.size < 32) {
                NLog.e(TAG, "unexpected field count for $code: ${fields.size}")
                return@forEach
            }
            val price = fields[3].toFloatOrNull() ?: 0f
            val preClose = fields[2].toFloatOrNull() ?: 0f
            result.add(
                StockInfo(
                    code = code,
                    name = fields[0],
                    // 未开盘或停牌时现价为 0，用昨收兜底避免显示成跌停
                    price = if (price == 0f) preClose else price,
                    preClose = preClose,
                    time = fields[30] + " " + fields[31]
                )
            )
        }
        return result
    }
}
