package gov.anzong.androidnga.activity.compose.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gov.anzong.androidnga.core.board.data.BoardEntity

private const val MAX_COLUMN = 3

private val EditBarColor = Color(0xFFF2FFC3)

/**
 * 可编辑的版面九宫格：长按进入编辑模式，选中的版面可以左右移动换位，也可以隐藏。
 *
 * 没有用手势拖拽——九宫格里手指拖动要和 LazyVerticalGrid 的滚动抢事件，
 * 小图标上精确落点也难。长按选中 + 明确的移动按钮更好操作，也更容易撤销。
 *
 * [group] 是这批版面所属的分组，排序按分组独立保存。
 */
@Composable
fun EditableBoardGrid(
    group: BoardEntity,
    forumBoardViewModel: ForumBoardViewModel,
    emptyContent: @Composable (() -> Unit)? = null
) {
    // 订阅这两个，隐藏或排序变化时整个格子才会重算
    val hiddenIds by forumBoardViewModel.hiddenIdsLiveData.observeAsState(emptySet())
    val orderVersion by forumBoardViewModel.orderVersionLiveData.observeAsState(0)

    var editingId by remember { mutableStateOf<String?>(null) }

    val boards = remember(group, hiddenIds, orderVersion) {
        forumBoardViewModel.visibleChildren(group)
    }
    // 选中的版面被隐藏或移走后，退出编辑态
    val editingIndex = boards.indexOfFirst { it.id == editingId }
    if (editingId != null && editingIndex < 0) {
        editingId = null
    }

    if (boards.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (emptyContent != null) {
                emptyContent()
            } else {
                Text(text = "这里还没有版面", color = Color.Gray)
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (editingIndex >= 0) {
            BoardEditBar(
                board = boards[editingIndex],
                canMoveLeft = editingIndex > 0,
                canMoveRight = editingIndex < boards.size - 1,
                onMoveLeft = {
                    forumBoardViewModel.saveBoardOrder(group, boards.swapped(editingIndex, editingIndex - 1))
                },
                onMoveRight = {
                    forumBoardViewModel.saveBoardOrder(group, boards.swapped(editingIndex, editingIndex + 1))
                },
                onHide = {
                    forumBoardViewModel.hideBoard(boards[editingIndex])
                    editingId = null
                },
                onDone = { editingId = null }
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(MAX_COLUMN),
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 8.dp)
        ) {
            items(boards.size) { index ->
                val board = boards[index]
                ForumBoardGridItemView(
                    child = board,
                    forumBoardViewModel = forumBoardViewModel,
                    onLongClick = { editingId = board.id },
                    selected = board.id == editingId
                )
            }
            item(span = { GridItemSpan(MAX_COLUMN) }) {
                val paddingValues = WindowInsets.navigationBars.asPaddingValues()
                Spacer(modifier = Modifier.height(paddingValues.calculateBottomPadding()))
            }
        }
    }
}

/**
 * 编辑模式下顶部的操作条。放在顶部而不是弹窗，是为了让用户能连续点几下移动，
 * 边点边看格子里的位置变化。
 */
@Composable
private fun BoardEditBar(
    board: BoardEntity,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onHide: () -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EditBarColor)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = board.name,
            color = Color.Black,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TextButton(onClick = onMoveLeft, enabled = canMoveLeft) { Text("← 前移") }
            TextButton(onClick = onMoveRight, enabled = canMoveRight) { Text("后移 →") }
            TextButton(onClick = onHide) { Text("隐藏", color = Color(0xFFD32F2F)) }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onDone) { Text("完成") }
        }
    }
}

private fun List<BoardEntity>.swapped(from: Int, to: Int): List<BoardEntity> {
    val result = toMutableList()
    val item = result.removeAt(from)
    result.add(to, item)
    return result
}
