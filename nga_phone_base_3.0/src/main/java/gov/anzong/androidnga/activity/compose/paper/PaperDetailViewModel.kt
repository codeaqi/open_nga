package gov.anzong.androidnga.activity.compose.paper

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gov.anzong.androidnga.activity.compose.paper.data.PaperBlock
import gov.anzong.androidnga.activity.compose.paper.data.PaperFullTextRepository
import gov.anzong.androidnga.activity.compose.paper.data.TranslateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PaperDetailViewModel : ViewModel() {

    val blocksLiveData: MutableLiveData<List<PaperBlock>> = MutableLiveData(emptyList())

    val loadingLiveData: MutableLiveData<Boolean> = MutableLiveData(false)

    /** 加载结束但没拿到内容时为 true，用来区分「加载中」和「真的没有」 */
    val failedLiveData: MutableLiveData<Boolean> = MutableLiveData(false)

    /** 原文 -> 译文。滚到哪翻到哪，翻好一句就更新一次 */
    val translationLiveData: MutableLiveData<Map<String, String>> = MutableLiveData(emptyMap())

    /** 是否显示翻译，可在顶栏开关 */
    val translateEnabledLiveData: MutableLiveData<Boolean> = MutableLiveData(true)

    private var loaded = false

    /** 正在翻译中的句子，避免同一句发多次请求 */
    private val pending = mutableSetOf<String>()

    private var translateJob: Job? = null

    fun loadFullText(arxivId: String) {
        if (loaded || arxivId.isEmpty()) {
            return
        }
        loaded = true
        fetch(arxivId)
    }

    fun retry(arxivId: String) {
        if (loadingLiveData.value == true || arxivId.isEmpty()) {
            return
        }
        fetch(arxivId)
    }

    fun toggleTranslate() {
        translateEnabledLiveData.value = translateEnabledLiveData.value != true
    }

    private fun fetch(arxivId: String) {
        loadingLiveData.value = true
        failedLiveData.value = false
        viewModelScope.launch {
            val blocks = withContext(Dispatchers.IO) {
                PaperFullTextRepository.getFullText(arxivId)
            }
            loadingLiveData.value = false
            if (blocks.isEmpty()) {
                failedLiveData.value = blocksLiveData.value.isNullOrEmpty()
                return@launch
            }
            blocksLiveData.value = blocks
        }
    }

    /**
     * 翻译一批句子（通常是当前屏幕可见的那些）。
     * 已翻过或正在翻的会跳过，串行请求避免被限流。
     */
    fun translateVisible(sentences: List<String>) {
        if (translateEnabledLiveData.value != true) {
            return
        }
        val todo = synchronized(pending) {
            sentences.filter { s ->
                s.isNotBlank() &&
                        translationLiveData.value?.containsKey(s) != true &&
                        s !in pending
            }.also { pending.addAll(it) }
        }
        if (todo.isEmpty()) {
            return
        }
        translateJob = viewModelScope.launch {
            for (sentence in todo) {
                val result = withContext(Dispatchers.IO) {
                    TranslateRepository.translate(sentence)
                }
                synchronized(pending) { pending.remove(sentence) }
                if (result != null) {
                    // 每翻好一句就刷新，不等整批结束
                    translationLiveData.value =
                        translationLiveData.value.orEmpty() + (sentence to result)
                }
            }
        }
    }
}
