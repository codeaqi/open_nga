package gov.anzong.androidnga.activity.compose.paper

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.alibaba.android.arouter.facade.annotation.Route
import com.justwen.androidnga.ui.compose.BaseComposeActivity
import com.justwen.androidnga.ui.compose.widget.TopAppBarData
import gov.anzong.androidnga.arouter.ARouterConstants

@Route(path = ARouterConstants.ACTIVITY_PAPER_LIST)
class PaperListActivity : BaseComposeActivity() {

    private val viewModel: PaperListViewModel by lazy {
        ViewModelProvider(this)[PaperListViewModel::class.java]
    }

    override fun getTopAppBarData(): TopAppBarData {
        val topAppBarData = TopAppBarData(title = "论文阅读")
        topAppBarData.navigationIconAction = { finish() }
        return topAppBarData
    }

    @Composable
    override fun ContentView() {
        PaperListView(viewModel)
    }
}
