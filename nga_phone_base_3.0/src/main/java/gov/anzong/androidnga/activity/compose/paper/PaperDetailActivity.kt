package gov.anzong.androidnga.activity.compose.paper

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import com.alibaba.android.arouter.facade.annotation.Route
import com.justwen.androidnga.ui.compose.BaseComposeActivity
import com.justwen.androidnga.ui.compose.widget.OptionMenuData
import com.justwen.androidnga.ui.compose.widget.TopAppBarData
import gov.anzong.androidnga.activity.compose.paper.data.PaperItem
import gov.anzong.androidnga.arouter.ARouterConstants

@Route(path = ARouterConstants.ACTIVITY_PAPER_DETAIL)
class PaperDetailActivity : BaseComposeActivity() {

    private val paper: PaperItem? by lazy {
        @Suppress("DEPRECATION")
        intent.getSerializableExtra(KEY_PAPER) as? PaperItem
    }

    private val viewModel: PaperDetailViewModel by lazy {
        ViewModelProvider(this)[PaperDetailViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        paper?.arxivId?.let { viewModel.loadFullText(it) }
    }

    override fun getTopAppBarData(): TopAppBarData {
        val topAppBarData = TopAppBarData(title = "论文阅读")
        topAppBarData.navigationIconAction = { finish() }
        topAppBarData.optionMenuData = listOf(
            OptionMenuData(title = "显示/隐藏翻译", action = {
                viewModel.toggleTranslate()
            })
        )
        return topAppBarData
    }

    @Composable
    override fun ContentView() {
        PaperDetailView(paper, viewModel)
    }

    companion object {
        const val KEY_PAPER = "paper_item"
    }
}
