package gov.anzong.androidnga.activity.compose.board

import android.os.Bundle
import androidx.compose.runtime.Composable
import com.justwen.androidnga.ui.compose.BaseComposeFragment

/**
 * 隐藏夹页面，由 FragmentTemplateActivity 承载。
 * 数据来自 [ForumBoardViewModel] 单例，所以这里不需要自己的 ViewModel。
 */
class HiddenBoardFragment : BaseComposeFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        requireActivity().title = "隐藏夹"
        super.onCreate(savedInstanceState)
    }

    @Composable
    override fun ContentView() {
        HiddenBoardView(ForumBoardViewModel)
    }
}
