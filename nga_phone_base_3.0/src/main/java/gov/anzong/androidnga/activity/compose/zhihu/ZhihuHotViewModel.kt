package gov.anzong.androidnga.activity.compose.zhihu

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gov.anzong.androidnga.activity.compose.zhihu.data.ZhihuHotItem
import gov.anzong.androidnga.activity.compose.zhihu.data.ZhihuHotRepository
import gov.anzong.androidnga.base.util.ToastUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ZhihuHotViewModel : ViewModel() {

    val hotLiveData: MutableLiveData<List<ZhihuHotItem>> = MutableLiveData(emptyList())

    val refreshingLiveData: MutableLiveData<Boolean> = MutableLiveData(false)

    init {
        // 先把上次的榜单显示出来（内存没有就读磁盘），旧了再后台悄悄拉新的，
        // 用户一进来就有内容，不用对着转圈等。
        val cached = ZhihuHotRepository.getCachedList()
        if (cached.isNotEmpty()) {
            hotLiveData.value = cached
            if (ZhihuHotRepository.isStale()) {
                refresh(silent = true)
            }
        } else {
            refresh()
        }
    }

    /**
     * 拉取热搜。热搜刷新频率低，不需要像自选股那样轮询。
     * 已有数据时刷新失败保留旧列表，只在完全没有数据时报错。
     */
    /**
     * [silent] 为 true 表示后台静默刷新：不显示下拉转圈，失败也不弹提示，
     * 继续用已展示的旧榜单。
     */
    fun refresh(silent: Boolean = false) {
        if (refreshingLiveData.value == true) {
            return
        }
        if (!silent) {
            refreshingLiveData.value = true
        }
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                ZhihuHotRepository.fetchHotList()
            }
            if (!silent) {
                refreshingLiveData.value = false
            }
            if (list.isEmpty()) {
                if (!silent && hotLiveData.value.isNullOrEmpty()) {
                    ToastUtils.error("热搜获取失败，请检查网络")
                }
                return@launch
            }
            hotLiveData.value = list
        }
    }
}
