package gov.anzong.androidnga.activity.compose.zhihu

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.alibaba.android.arouter.facade.annotation.Route
import com.justwen.androidnga.ui.compose.BaseComposeActivity
import com.justwen.androidnga.ui.compose.widget.TopAppBarData
import gov.anzong.androidnga.activity.compose.zhihu.data.ZhihuHotItem
import gov.anzong.androidnga.arouter.ARouterConstants

/**
 * 知乎热搜详情页。
 *
 * 标题/热度/问题描述来自列表接口，秒开；网友回答由 ZhihuAnswerFetcher 用隐藏
 * WebView 抓回来后原生渲染，页面上不出现知乎的 UI、引流和广告。
 */
@Route(path = ARouterConstants.ACTIVITY_ZHIHU_DETAIL)
class ZhihuDetailActivity : BaseComposeActivity() {

    private val item: ZhihuHotItem? by lazy {
        @Suppress("DEPRECATION")
        intent.getSerializableExtra(KEY_ITEM) as? ZhihuHotItem
    }

    private val viewModel: ZhihuDetailViewModel by lazy {
        ViewModelProvider(this)[ZhihuDetailViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        item?.url?.let { viewModel.loadAnswers(it) }
    }

    override fun getTopAppBarData(): TopAppBarData {
        val topAppBarData = TopAppBarData(title = "知乎热搜")
        topAppBarData.navigationIconAction = { finish() }
        return topAppBarData
    }

    @Composable
    override fun ContentView() {
        ZhihuDetailView(item, viewModel)
    }

    companion object {
        const val KEY_ITEM = "zhihu_item"
    }
}
