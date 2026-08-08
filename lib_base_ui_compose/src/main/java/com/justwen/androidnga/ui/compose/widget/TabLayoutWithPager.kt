package com.justwen.androidnga.ui.compose.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ScrollableTabRow
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Preview
@Composable
fun TabLayoutWithPager(
    tabs: List<String> = arrayListOf("1", "2"),
    initialPage: Int = 0,
    fixed: Boolean = false,
    content: @Composable ((index: Int) -> Unit)? = null,
) {
    val pagerState = rememberPagerState(pageCount = { tabs.size }, initialPage = initialPage)
    val coroutineScope = rememberCoroutineScope()
    Column {
        if (fixed) {
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                modifier = Modifier
                    .background(color = MaterialTheme.colors.primary)
            ) {
                TabRowItems(tabs, pagerState, coroutineScope)
            }
        } else {
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 0.dp,
                modifier = Modifier
                    .background(color = MaterialTheme.colors.primary)
            ) {
                TabRowItems(tabs, pagerState, coroutineScope)
            }
        }
        // 相邻页提前组合好。默认值 0 意味着手指按下、页面开始跟手的那一刻才去组合下一页
        // ——整页的组合和测量全落在滑动的头几帧里，屏幕是 120Hz 的话一帧只有 8.3ms，
        // 必然掉帧，手感就是「一滑就顿一下」。提前一页换来的是滑动全程只做位移。
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1
        ) { pageIndex -> content?.invoke(pageIndex) }
    }

}

@Composable
private fun TabRowItems(
    tabs: List<String> = emptyList(),
    pagerState: PagerState,
    coroutineScope: CoroutineScope
) {
    tabs.forEachIndexed { index, title ->
        Tab(
            selected = pagerState.currentPage == index,
            onClick = {
                coroutineScope.launch {
                    pagerState.animateScrollToPage(index)
                }
            },
            text = {
                // 固定宽度的 TabRow 把每个 Tab 挤成等分的窄条，Tab 自身还有左右 16dp
                // 内边距，「IT软硬件」这类稍长的标题默认会折成两行。
                // maxLines=1 + softWrap=false 保证不换行，字号收一档避免被截断。
                Text(
                    text = title,
                    maxLines = 1,
                    softWrap = false,
                    fontSize = 13.sp
                )
            })
    }
}