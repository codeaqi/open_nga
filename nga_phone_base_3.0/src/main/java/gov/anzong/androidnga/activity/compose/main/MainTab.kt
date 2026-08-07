package gov.anzong.androidnga.activity.compose.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.justwen.androidnga.ui.compose.widget.TabLayoutWithPager
import gov.anzong.androidnga.R
import gov.anzong.androidnga.activity.compose.board.ForumBoardGridContent
import gov.anzong.androidnga.activity.compose.board.ForumBoardViewModel
import gov.anzong.androidnga.arouter.ARouterConstants
import gov.anzong.androidnga.core.board.data.BoardEntity

/**
 * 首页底部导航的三个 Tab。
 * [label] 是底部导航栏上的文字，[title] 是顶部标题栏的文字，两者可以不同。
 * [title] 为 null 表示标题取自板块数据（随 board_list.json 变化）；给了值就固定显示该值。
 */
enum class MainTab(val label: String, val icon: ImageVector, val title: String?) {
    FORUM("万象庭", Icons.Default.Home, "知行合一"),
    BOOKMARK("我的收藏", Icons.Default.Star, "知行合一"),
    MINE("我的", Icons.Default.Person, "我的"),
}

private val BottomBarColor = Color(0xFFF2FFC3)

private val BottomBarCorner = 20.dp

/**
 * 底部导航：黑色底 + 上方两角圆弧。
 *
 * 圆角要落在导航条本身而不是它外面的容器上，所以这里自己套一层 Box 承担系统手势条的
 * inset，NavigationBar 用 windowInsets = 0 交出 inset 处理权，否则圆角下方会露出一截直角。
 */
@Composable
fun MainBottomBar(current: MainTab, onSelect: (MainTab) -> Unit) {
    // 底色是浅黄绿，文字用黑色；未选中的调淡一些拉开层次
    val selectedColor = Color.Black
    val unselectedColor = Color(0x99000000)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = BottomBarCorner, topEnd = BottomBarCorner))
            .background(BottomBarColor)
            .navigationBarsPadding()
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier.height(58.dp)
        ) {
            MainTab.entries.forEach { tab ->
                NavigationBarItem(
                    selected = tab == current,
                    onClick = { onSelect(tab) },
                    icon = {
                        // NavigationBarItem 内部图标与文字的间距是固定的，改不了；
                        // 给图标加上边距把它整体压下来，视觉上就和文字贴近了
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .size(22.dp)
                        )
                    },
                    label = {
                        Text(
                            text = tab.label,
                            fontSize = 12.sp,
                            modifier = Modifier.offset(y = (-3).dp)
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = selectedColor,
                        selectedTextColor = selectedColor,
                        unselectedIconColor = unselectedColor,
                        unselectedTextColor = unselectedColor,
                        // 去掉选中项背后的药丸形色块
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }
    }
}

/**
 * 「网事杂谈」页。按语义取第一个非收藏板块，不依赖 boardList 的下标顺序。
 *
 * 该板块下若还有子分组（当前数据是「网事杂谈」和「IT软硬件」），拆成二级 Tab；
 * 否则直接铺一个九宫格。
 */
@Composable
fun ForumTabContent(forumBoardViewModel: ForumBoardViewModel) {
    val boardData by forumBoardViewModel.boardLiveData.observeAsState()
    val forumBoard = boardData?.firstOrNull {
        it.type != BoardEntity.BoardType.BOOKMARK
    }
    if (forumBoard == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "版面加载中…", color = Color.Gray)
        }
        return
    }

    val groups = forumBoard.children.orEmpty()
        .filter { it.type == BoardEntity.BoardType.GROUP }
    if (groups.isEmpty()) {
        ForumBoardGridContent(forumBoard, forumBoardViewModel)
        return
    }

    // 自定义内容源入口：非 NGA 板块，点进去打开对应页面。以后要加内容源就扩充这个列表。
    val customSources = listOf(
        CustomSource("知乎热搜", ARouterConstants.ACTIVITY_ZHIHU_HOT, R.drawable.ic_zhihu),
        CustomSource("论文阅读", ARouterConstants.ACTIVITY_PAPER_LIST, R.drawable.ic_paper)
    )
    val customTabName = "网事闲聊"

    TabLayoutWithPager(
        tabs = groups.map { it.name } + customTabName,
        initialPage = 0,
        fixed = true
    ) { index ->
        if (index == groups.size) {
            CustomSourceGridView(customSources)
        } else {
            ForumBoardGridContent(groups[index], forumBoardViewModel)
        }
    }
}
