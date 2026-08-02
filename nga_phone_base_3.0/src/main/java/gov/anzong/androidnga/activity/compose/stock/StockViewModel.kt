package gov.anzong.androidnga.activity.compose.stock

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gov.anzong.androidnga.activity.compose.stock.data.DividendInfo
import gov.anzong.androidnga.activity.compose.stock.data.DividendRepository
import gov.anzong.androidnga.activity.compose.stock.data.StockInfo
import gov.anzong.androidnga.activity.compose.stock.data.StockRepository
import gov.anzong.androidnga.activity.compose.stock.data.StockTarget
import gov.anzong.androidnga.activity.compose.stock.data.TradingHours
import gov.anzong.androidnga.base.util.ToastUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class StockViewModel : ViewModel() {

    val stockLiveData: MutableLiveData<List<StockInfo>> = MutableLiveData(emptyList())

    val refreshingLiveData: MutableLiveData<Boolean> = MutableLiveData(false)

    /** 各只股票的目标价，key 为带前缀的股票代码 */
    val targetLiveData: MutableLiveData<Map<String, StockTarget>> = MutableLiveData(emptyMap())

    /** 各只股票的每股股息，key 为带前缀的股票代码 */
    val dividendLiveData: MutableLiveData<Map<String, DividendInfo>> = MutableLiveData(emptyMap())

    /** 轮询间隔。交易时段内每隔这么久刷一次行情 */
    private val pollInterval = TimeUnit.SECONDS.toMillis(3)

    private var pollJob: Job? = null

    init {
        targetLiveData.value = StockRepository.loadTargets()
        refresh()
    }

    /**
     * 页面回到前台时调用：立即刷一次，并在交易时段内开始轮询。
     * 非交易时段只刷这一次——收盘后价格不再变化，轮询纯属浪费。
     */
    fun onResume() {
        refresh()
        startPolling()
    }

    /** 页面离开前台时调用，停掉轮询 */
    fun onPause() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(pollInterval)
                // 每轮都重新判断：可能刚好在轮询期间开盘或收盘
                if (TradingHours.isTrading()) {
                    refresh(silent = true)
                }
            }
        }
    }

    override fun onCleared() {
        onPause()
        super.onCleared()
    }

    /**
     * 拉取缺失或已过期的分红数据。分红一年才变一两次，所以不跟着行情刷新，
     * 只补没有缓存的那些，一只票一个请求。
     */
    private fun refreshDividends(codes: List<String>) {
        val cached = mutableMapOf<String, DividendInfo>()
        val missing = mutableListOf<String>()
        codes.forEach { code ->
            val info = DividendRepository.getCached(code)
            if (info != null) {
                cached[code] = info
            } else {
                missing.add(code)
            }
        }
        // 先把缓存里已有的显示出来，避免等网络
        if (cached.isNotEmpty()) {
            dividendLiveData.value = dividendLiveData.value.orEmpty() + cached
        }
        if (missing.isEmpty()) {
            return
        }
        viewModelScope.launch {
            val fetched = withContext(Dispatchers.IO) {
                missing.mapNotNull { code ->
                    DividendRepository.fetchDividend(code)?.let { code to it }
                }.toMap()
            }
            if (fetched.isNotEmpty()) {
                dividendLiveData.value = dividendLiveData.value.orEmpty() + fetched
                recalcAutoTargets(fetched)
            }
        }
    }

    /**
     * 分红数据变了就重算开了自动计算的那些股票的目标价。
     * 公司调整分红后，原来存的价格会失真，不重算就会一直按旧股息提醒。
     */
    private fun recalcAutoTargets(dividends: Map<String, DividendInfo>) {
        val targets = targetLiveData.value ?: return
        var changed = false
        dividends.forEach { (code, info) ->
            val target = targets[code] ?: return@forEach
            if (!target.autoCalc) {
                return@forEach
            }
            val calc = StockTarget.calculate(info.perShareDividend, target.targetYield)
                ?: return@forEach
            if (calc.first != target.buildPrice) {
                StockRepository.saveTarget(
                    code,
                    target.copy(
                        buildPrice = calc.first,
                        addPrice = calc.second,
                        fullPrice = calc.third
                    )
                )
                changed = true
            }
        }
        if (changed) {
            targetLiveData.value = StockRepository.loadTargets()
        }
    }

    fun saveTarget(code: String, target: StockTarget) {
        StockRepository.saveTarget(code, target)
        targetLiveData.value = StockRepository.loadTargets()
    }

    /**
     * @param silent 轮询触发时为 true：失败不弹 toast，也不显示刷新状态。
     * 每 3 秒一次的轮询若逐次报错，会把屏幕糊满 toast。
     */
    fun refresh(silent: Boolean = false) {
        val codes = StockRepository.loadWatchList()
        if (codes.isEmpty()) {
            stockLiveData.value = emptyList()
            return
        }
        if (!silent) {
            refreshingLiveData.value = true
        }
        viewModelScope.launch {
            val quotes = withContext(Dispatchers.IO) {
                StockRepository.fetchQuotes(codes)
            }
            refreshingLiveData.value = false
            if (quotes.isEmpty()) {
                if (!silent) {
                    ToastUtils.error("行情获取失败，请检查网络")
                }
                // 保留上一次的数据，不要把列表清空
                return@launch
            }
            stockLiveData.value = quotes
            refreshDividends(quotes.map { it.code })
        }
    }

    fun addStock(input: String) {
        val code = StockRepository.normalizeCode(input)
        if (code == null) {
            ToastUtils.error("请输入正确的6位股票代码")
            return
        }
        StockRepository.addStock(code)
        refresh()
    }

    fun removeStock(stock: StockInfo) {
        StockRepository.removeStock(stock.code)
        // 直接从当前列表移除，无需重新请求
        stockLiveData.value = stockLiveData.value?.filter { it.code != stock.code }
    }

    /**
     * 上移/下移一只自选股。直接调整内存里的顺序，不重新拉行情——
     * 换个位置不该等一次网络请求。
     */
    fun moveStock(stock: StockInfo, up: Boolean) {
        val order = StockRepository.moveStock(stock.code, up)
        val current = stockLiveData.value ?: return
        val indexOf = order.withIndex().associate { (i, code) -> code to i }
        stockLiveData.value = current.sortedBy { indexOf[it.code] ?: Int.MAX_VALUE }
    }

    fun saveNote(code: String, note: String, showNote: Boolean) {
        val target = targetLiveData.value?.get(code) ?: StockTarget()
        StockRepository.saveTarget(code, target.copy(note = note, showNote = showNote))
        targetLiveData.value = StockRepository.loadTargets()
    }
}
