package gov.anzong.androidnga.activity.compose.zhihu

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import gov.anzong.androidnga.activity.compose.zhihu.data.AnswerCacheData
import gov.anzong.androidnga.activity.compose.zhihu.data.ZhihuAnswer
import gov.anzong.androidnga.activity.compose.zhihu.data.ZhihuAnswerFetcher
import gov.anzong.androidnga.activity.compose.zhihu.data.ZhihuBlock
import gov.anzong.androidnga.activity.compose.zhihu.data.ZhihuCache
import gov.anzong.androidnga.activity.compose.zhihu.data.ZhihuComment
import kotlinx.coroutines.launch

class ZhihuDetailViewModel(application: Application) : AndroidViewModel(application) {

    val answersLiveData: MutableLiveData<List<ZhihuAnswer>> = MutableLiveData(emptyList())

    val loadingLiveData: MutableLiveData<Boolean> = MutableLiveData(false)

    /** 加载更多时的 loading，和首次加载分开，避免整页转圈 */
    val loadingMoreLiveData: MutableLiveData<Boolean> = MutableLiveData(false)

    /** 抓取结束但一条回答都没有时置 true，用来区分「加载中」和「真的没有」 */
    val emptyLiveData: MutableLiveData<Boolean> = MutableLiveData(false)

    /** 页面里抽到的问题描述（含配图），比列表接口的更完整；为空时沿用列表带来的 */
    val detailLiveData: MutableLiveData<List<ZhihuBlock>> = MutableLiveData(emptyList())

    /** 是否还有更多回答可加载 */
    val hasMoreLiveData: MutableLiveData<Boolean> = MutableLiveData(false)

    /** 回答总数，接口给不出时为 0 */
    val totalLiveData: MutableLiveData<Int> = MutableLiveData(0)

    /** 展开的回答 id 集合。知乎回答很长，默认折叠只显示前几行 */
    val expandedLiveData: MutableLiveData<Set<String>> = MutableLiveData(emptySet())

    /** answerId -> 该回答的评论。null 表示还没加载 */
    val commentsLiveData: MutableLiveData<Map<String, List<ZhihuComment>>> =
        MutableLiveData(emptyMap())

    /** 正在加载评论的 answerId 集合 */
    val commentLoadingLiveData: MutableLiveData<Set<String>> = MutableLiveData(emptySet())

    /** 展开评论区的 answerId 集合 */
    val commentShownLiveData: MutableLiveData<Set<String>> = MutableLiveData(emptySet())

    private var loaded = false

    private var questionUrl = ""

    fun loadAnswers(url: String) {
        if (loaded || url.isEmpty()) {
            return
        }
        loaded = true
        questionUrl = url

        // 有缓存就先显示（哪怕已经旧了），过期的再在后台悄悄刷新，
        // 用户不用对着转圈等。杀掉后台重进也能从磁盘读到上次的内容。
        val cached = ZhihuCache.getAnswers(url)
        if (cached != null) {
            detailLiveData.value = cached.detailBlocks
            answersLiveData.value = cached.answers
            totalLiveData.value = cached.total
            hasMoreLiveData.value = cached.hasMore
            emptyLiveData.value = false
            if (ZhihuCache.isStale(url)) {
                fetch(url, offset = 0, append = false, silent = true)
            }
            return
        }
        fetch(url, offset = 0, append = false)
    }

    fun refresh(url: String) {
        if (loadingLiveData.value == true || url.isEmpty()) {
            return
        }
        questionUrl = url
        // 主动重试时丢掉缓存，强制重新抓
        ZhihuCache.invalidate(url)
        fetch(url, offset = 0, append = false)
    }

    /** 手动加载下一页 */
    fun loadMore() {
        if (loadingMoreLiveData.value == true || questionUrl.isEmpty()) {
            return
        }
        if (hasMoreLiveData.value != true) {
            return
        }
        val offset = answersLiveData.value?.size ?: 0
        fetch(questionUrl, offset = offset, append = true)
    }

    /**
     * [silent] 为 true 表示后台静默刷新：不显示 loading，失败也保留已展示的旧内容。
     */
    private fun fetch(url: String, offset: Int, append: Boolean, silent: Boolean = false) {
        if (append) {
            loadingMoreLiveData.value = true
        } else if (!silent) {
            loadingLiveData.value = true
            emptyLiveData.value = false
        }
        viewModelScope.launch {
            // WebView 必须在主线程，fetch 内部自己切，这里不要包 withContext(IO)
            val result = ZhihuAnswerFetcher.fetch(getApplication(), url, offset)
            loadingLiveData.value = false
            loadingMoreLiveData.value = false

            if (result == null) {
                // 静默刷新失败就当无事发生，继续用已展示的旧内容
                if (!append && !silent) {
                    emptyLiveData.value = answersLiveData.value.isNullOrEmpty()
                }
                return@launch
            }
            // 静默刷新没抓到东西也保留旧内容，别把页面刷空
            if (silent && result.answers.isEmpty()) {
                return@launch
            }
            if (result.detailBlocks.isNotEmpty()) {
                detailLiveData.value = result.detailBlocks
            }

            val merged = if (append) {
                val old = answersLiveData.value.orEmpty()
                // 接口分页偶尔会重复，按 id 去重
                val seen = old.mapTo(mutableSetOf()) { it.id }
                old + result.answers.filter { it.id.isEmpty() || seen.add(it.id) }
            } else {
                result.answers
            }
            answersLiveData.value = merged

            if (result.total > 0) {
                totalLiveData.value = result.total
            }
            // 接口给了 isEnd 就用它，否则按「还没取满总数」判断
            hasMoreLiveData.value = when {
                result.answers.isEmpty() -> false
                result.isEnd -> false
                result.total > 0 -> merged.size < result.total
                else -> result.answers.size >= ZhihuAnswerFetcher.PAGE_SIZE
            }
            if (!append && !silent) {
                emptyLiveData.value = result.answers.isEmpty()
            }

            // 存缓存，下次进来直接显示（含已翻的页）
            if (merged.isNotEmpty()) {
                ZhihuCache.putAnswers(
                    url,
                    AnswerCacheData(
                        detailBlocks = detailLiveData.value.orEmpty(),
                        answers = merged,
                        total = totalLiveData.value ?: 0,
                        hasMore = hasMoreLiveData.value ?: false
                    )
                )
            }
        }
    }

    /** 折叠/展开某条回答 */
    fun toggleExpand(answerId: String) {
        val cur = expandedLiveData.value.orEmpty()
        expandedLiveData.value = if (answerId in cur) cur - answerId else cur + answerId
    }

    fun isExpanded(answerId: String): Boolean = answerId in expandedLiveData.value.orEmpty()

    /**
     * 展开/收起某条回答的评论。首次展开时才去抓，抓过就直接用缓存。
     */
    fun toggleComments(answerId: String) {
        if (answerId.isEmpty()) return
        val shown = commentShownLiveData.value.orEmpty()
        if (answerId in shown) {
            commentShownLiveData.value = shown - answerId
            return
        }
        commentShownLiveData.value = shown + answerId

        if (commentsLiveData.value.orEmpty().containsKey(answerId)) {
            return
        }
        if (answerId in commentLoadingLiveData.value.orEmpty()) {
            return
        }
        // 抓过的评论直接用缓存，不再转圈
        ZhihuCache.getComments(answerId)?.let { cached ->
            commentsLiveData.value = commentsLiveData.value.orEmpty() + (answerId to cached)
            return
        }
        commentLoadingLiveData.value = commentLoadingLiveData.value.orEmpty() + answerId
        viewModelScope.launch {
            val list = ZhihuAnswerFetcher.fetchComments(getApplication(), answerId, questionUrl)
            commentLoadingLiveData.value = commentLoadingLiveData.value.orEmpty() - answerId
            commentsLiveData.value = commentsLiveData.value.orEmpty() +
                    (answerId to list.orEmpty())
            if (!list.isNullOrEmpty()) {
                ZhihuCache.putComments(answerId, list)
            }
        }
    }
}
