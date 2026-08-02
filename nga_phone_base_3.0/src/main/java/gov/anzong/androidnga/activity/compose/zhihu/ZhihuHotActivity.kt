package gov.anzong.androidnga.activity.compose.zhihu

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.alibaba.android.arouter.facade.annotation.Route
import com.justwen.androidnga.ui.compose.BaseComposeActivity
import com.justwen.androidnga.ui.compose.widget.TopAppBarData
import gov.anzong.androidnga.arouter.ARouterConstants

@Route(path = ARouterConstants.ACTIVITY_ZHIHU_HOT)
class ZhihuHotActivity : BaseComposeActivity() {

    private val viewModel: ZhihuHotViewModel by lazy {
        ViewModelProvider(this)[ZhihuHotViewModel::class.java]
    }

    override fun getTopAppBarData(): TopAppBarData {
        val topAppBarData = TopAppBarData(title = "知乎热搜")
        topAppBarData.navigationIconAction = { finish() }
        return topAppBarData
    }

    @Composable
    override fun ContentView() {
        ZhihuHotView(viewModel)
    }
}
