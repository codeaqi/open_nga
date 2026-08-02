package gov.anzong.androidnga.activity.compose.paper

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gov.anzong.androidnga.activity.compose.paper.data.PaperItem
import gov.anzong.androidnga.activity.compose.paper.data.PaperRepository
import gov.anzong.androidnga.base.util.ToastUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PaperListViewModel : ViewModel() {

    val paperLiveData: MutableLiveData<List<PaperItem>> = MutableLiveData(emptyList())

    val refreshingLiveData: MutableLiveData<Boolean> = MutableLiveData(false)

    init {
        // 先显示上次的（内存没有就读磁盘），旧了再后台悄悄更新
        val cached = PaperRepository.getCachedList()
        if (cached.isNotEmpty()) {
            paperLiveData.value = cached
            if (PaperRepository.isStale()) {
                refresh(silent = true)
            }
        } else {
            refresh()
        }
    }

    /**
     * [silent] 为 true 表示后台静默刷新：不转圈、失败不提示，保留旧内容。
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
                PaperRepository.fetchPapers()
            }
            if (!silent) {
                refreshingLiveData.value = false
            }
            if (list.isEmpty()) {
                if (!silent && paperLiveData.value.isNullOrEmpty()) {
                    ToastUtils.error("论文加载失败，请检查网络")
                }
                return@launch
            }
            paperLiveData.value = list
        }
    }
}
