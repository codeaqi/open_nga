package gov.anzong.androidnga.activity.compose.board

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.justwent.androidnga.bu.UserManager
import gov.anzong.androidnga.R
import gov.anzong.androidnga.base.util.ContextUtils
import gov.anzong.androidnga.core.board.data.BoardEntity
import sp.phone.common.ApiConstants
import kotlin.math.abs


@Composable
fun ForumBoardGroupView(board: BoardEntity, context: Context = ContextUtils.getContext()) {
    Row(modifier = Modifier.padding(16.dp)) {
        Image(
            modifier = Modifier.size(24.dp),
            painter = painterResource(id = R.drawable.default_board_icon),
            contentDescription = "",
        )
        Text(
            modifier = Modifier.padding(start = 8.dp),
            text = board.name,
            color = Color(context.resources.getColor(R.color.text_color, null)),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ForumBoardGridItemView(
    child: BoardEntity,
    forumBoardViewModel: ForumBoardViewModel,
    context: Context = ContextUtils.getContext(),
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false
) {
    val paddingValue = 4.dp
    val imageSize = 48.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .padding(paddingValue)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) Color(0x332196F3) else Color.Transparent
            )
            .combinedClickable(
                onClick = { forumBoardViewModel.showTopicList(child) },
                onLongClick = { onLongClick?.invoke() }
            )
    ) {
        Spacer(modifier = Modifier.height(paddingValue))
        val resId = getResId(child)
        if (resId > 0) {
            Image(
                modifier = Modifier.size(imageSize),
                painter = painterResource(id = resId),
                contentDescription = ""
            )
        } else {
            val url = getResUrl(child)
            Image(
                modifier = Modifier.size(imageSize),
                painter = rememberAsyncImagePainter(
                    model = url,
                    placeholder = painterResource(id = R.drawable.default_board_icon),
                    error = painterResource(id = R.drawable.default_board_icon)
                ),
                contentDescription = ""
            )
        }
        Text(
            modifier = Modifier
                .padding(top = paddingValue, bottom = paddingValue),
            color = Color(context.resources.getColor(R.color.text_color, null)),
            text = child.name
        )
        Spacer(modifier = Modifier.height(paddingValue))
    }
}

private fun getResUrl(board: BoardEntity): String {
    // 指定了借用图标就按那个版面取，否则合集用 stid、普通版面用 fid
    val url = if (board.iconFid != 0) {
        String.format(ApiConstants.URL_BOARD_ICON, board.iconFid)
    } else if (board.stid != 0) {
        String.format(ApiConstants.URL_BOARD_ICON_STID, board.stid)
    } else {
        String.format(ApiConstants.URL_BOARD_ICON, board.fid)
    }
    return url
}


private fun getResId(board: BoardEntity): Int {
    val fid = if (board.iconFid != 0) board.iconFid else board.fid
    // 合集本身没有内置图标，除非借用了别的版面
    if (board.iconFid == 0 && board.stid != 0) {
        return 0
    }

    val resName = if (fid > 0) "p$fid" else "p_" + abs(fid)
    return ContextUtils.getResources()
        .getIdentifier(resName, "drawable", ContextUtils.getContext().packageName)
}

@Composable
fun ForumBoardBookmarkContent(bookmark: BoardEntity, forumBoardViewModel: ForumBoardViewModel) {
    Column(Modifier.fillMaxSize()) {

        if (UserManager.getUserList().size == 1) {
            Text(modifier = Modifier.padding(8.dp), text = "建议登录多个账号，可有效改善跳转系统浏览器问题")
        }

        EditableBoardGrid(
            group = bookmark,
            forumBoardViewModel = forumBoardViewModel
        ) {
            Text(
                text = "还没有收藏的版面\n在版面列表长按，或用左上角菜单的「添加版面ID」添加",
                textAlign = TextAlign.Center,
                color = Color.Gray
            )
        }
    }
}

/**
 * 版面九宫格。传进来的分组下若还有子分组（GROUP），说明它是个容器，
 * 由调用方拆成二级 Tab 分别渲染；这里只负责渲染一层版面。
 */
@Composable
fun ForumBoardGridContent(
    boardData: BoardEntity,
    forumBoardViewModel: ForumBoardViewModel
) {
    EditableBoardGrid(group = boardData, forumBoardViewModel = forumBoardViewModel)
}


