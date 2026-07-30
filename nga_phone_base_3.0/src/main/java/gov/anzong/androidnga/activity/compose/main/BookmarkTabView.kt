package gov.anzong.androidnga.activity.compose.main

import androidx.compose.runtime.Composable
import com.justwen.androidnga.ui.compose.widget.TabLayoutWithPager
import gov.anzong.androidnga.activity.compose.board.ForumBoardBookmarkContent
import gov.anzong.androidnga.activity.compose.board.ForumBoardViewModel
import gov.anzong.androidnga.activity.compose.note.NoteView
import gov.anzong.androidnga.activity.compose.note.NoteViewModel
import gov.anzong.androidnga.activity.compose.stock.StockView
import gov.anzong.androidnga.activity.compose.stock.StockViewModel

/**
 * 「我的收藏」页：三个二级 Tab。
 * 我的思考与自选股都不是版面，不进板块数据，只在这里与收藏并列展示。
 */
@Composable
fun BookmarkTabView(
    forumBoardViewModel: ForumBoardViewModel,
    stockViewModel: StockViewModel,
    noteViewModel: NoteViewModel
) {
    // 默认落在「版面」，它才是这个页面的主要内容
    TabLayoutWithPager(
        tabs = listOf("我的思考", "版面", "自选股"),
        initialPage = 1,
        fixed = true
    ) { index ->
        when (index) {
            0 -> NoteView(noteViewModel)
            1 -> ForumBoardBookmarkContent(forumBoardViewModel.bookmarkBoard, forumBoardViewModel)
            else -> StockView(stockViewModel)
        }
    }
}
