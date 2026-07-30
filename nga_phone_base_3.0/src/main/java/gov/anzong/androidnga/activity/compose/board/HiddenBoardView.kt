package gov.anzong.androidnga.activity.compose.board

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 隐藏夹：列出所有被隐藏的版面。
 * 点名字直接进版面，右侧「恢复」把它放回原来的分组。
 */
@Composable
fun HiddenBoardView(forumBoardViewModel: ForumBoardViewModel) {
    val hiddenIds by forumBoardViewModel.hiddenIdsLiveData.observeAsState(emptySet())
    val boards = forumBoardViewModel.hiddenBoards()

    if (hiddenIds.isEmpty() || boards.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "隐藏夹是空的\n在版面上长按可以把它隐藏起来",
                textAlign = TextAlign.Center,
                color = Color.Gray
            )
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(boards.size) { index ->
            val board = boards[index]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { forumBoardViewModel.showTopicList(board) }
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = board.name,
                        fontSize = 16.sp,
                        color = MaterialTheme.colors.onBackground
                    )
                }
                TextButton(
                    onClick = { forumBoardViewModel.showBoard(board.id) },
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("恢复")
                }
            }
            HorizontalDivider()
        }
    }
}
