package gov.anzong.androidnga.activity.compose.stock

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gov.anzong.androidnga.activity.compose.stock.data.DividendInfo
import gov.anzong.androidnga.activity.compose.stock.data.DividendRepository
import gov.anzong.androidnga.activity.compose.stock.data.StockInfo
import gov.anzong.androidnga.activity.compose.stock.data.StockRepository
import gov.anzong.androidnga.activity.compose.stock.data.StockTarget
import gov.anzong.androidnga.base.util.ToastUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StockViewModel : ViewModel() {

    val stockLiveData: MutableLiveData<List<StockInfo>> = MutableLiveData(emptyList())

    val refreshingLiveData: MutableLiveData<Boolean> = MutableLiveData(false)

    /** 各只股票的目标价，key 为带前缀的股票代码 */
    val targetLiveData: MutableLiveData<Map<String, StockTarget>> = MutableLiveData(emptyMap())

    /** 各只股票的每股股息，key 为带前缀的股票代码 */
    val dividendLiveData: MutableLiveData<Map<String, DividendInfo>> = MutableLiveData(emptyMap())

    init {
        targetLiveData.value = StockRepository.loadTargets()
        refresh()
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
            }
        }
    }

    fun saveTarget(code: String, target: StockTarget) {
        StockRepository.saveTarget(code, target)
        targetLiveData.value = StockRepository.loadTargets()
    }

    fun refresh() {
        val codes = StockRepository.loadWatchList()
        if (codes.isEmpty()) {
            stockLiveData.value = emptyList()
            return
        }
        refreshingLiveData.value = true
        viewModelScope.launch {
            val quotes = withContext(Dispatchers.IO) {
                StockRepository.fetchQuotes(codes)
            }
            refreshingLiveData.value = false
            if (quotes.isEmpty()) {
                ToastUtils.error("行情获取失败，请检查网络")
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
