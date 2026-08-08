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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import java.util.concurrent.ConcurrentHashMap
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

private val ItemPadding = 4.dp

private val IconSize = 48.dp

/**
 * 版面九宫格里的一格。
 *
 * [textColor] 由调用方传入而不是在这里查资源：一屏有十几格，每格每次重组都查一次
 * 颜色资源是白花的钱，调用方查一次传下来即可。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ForumBoardGridItemView(
    child: BoardEntity,
    forumBoardViewModel: ForumBoardViewModel,
    textColor: Color,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .padding(ItemPadding)
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
        Spacer(modifier = Modifier.height(ItemPadding))
        val resId = remember(child.id) { getResId(child) }
        if (resId > 0) {
            Image(
                modifier = Modifier.size(IconSize),
                painter = painterResource(id = resId),
                contentDescription = ""
            )
        } else {
            // 网络图标按显示尺寸解码，否则每个格子都拿原图大小的 Bitmap 占内存缓存
            val iconPx = with(LocalDensity.current) { IconSize.roundToPx() }
            val appContext = LocalContext.current.applicationContext
            val request = remember(child.id, iconPx) {
                ImageRequest.Builder(appContext)
                    .data(getResUrl(child))
                    .size(iconPx, iconPx)
                    .build()
            }
            Image(
                modifier = Modifier.size(IconSize),
                painter = rememberAsyncImagePainter(
                    model = request,
                    placeholder = painterResource(id = R.drawable.default_board_icon),
                    error = painterResource(id = R.drawable.default_board_icon)
                ),
                contentDescription = ""
            )
        }
        Text(
            modifier = Modifier
                .padding(top = ItemPadding, bottom = ItemPadding),
            color = textColor,
            text = child.name
        )
        Spacer(modifier = Modifier.height(ItemPadding))
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


/**
 * fid → 内置图标资源 id 的缓存。
 *
 * getIdentifier 是拿字符串去资源表里找，命中慢、找不到更慢，而当前 board_list 里
 * 大部分版面根本没有内置图标，走的全是「找不到」这条路。查询结果在运行期不会变，
 * 缓存住，一个 fid 只查一次。
 */
private val boardIconResIds = ConcurrentHashMap<Int, Int>()

private fun getResId(board: BoardEntity): Int {
    // 合集本身没有内置图标，除非借用了别的版面
    if (board.iconFid == 0 && board.stid != 0) {
        return 0
    }
    val fid = if (board.iconFid != 0) board.iconFid else board.fid
    return boardIconResIds.getOrPut(fid) {
        val resName = if (fid > 0) "p$fid" else "p_" + abs(fid)
        ContextUtils.getResources()
            .getIdentifier(resName, "drawable", ContextUtils.getContext().packageName)
    }
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


