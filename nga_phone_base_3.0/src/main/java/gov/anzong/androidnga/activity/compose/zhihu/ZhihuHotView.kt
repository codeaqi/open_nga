package gov.anzong.androidnga.activity.compose.zhihu

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Text
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gov.anzong.androidnga.activity.compose.zhihu.data.ZhihuHotItem
import gov.anzong.androidnga.arouter.ARouterConstants
import sp.phone.util.ARouterUtils

/** 热搜前三名的排名颜色，突出一下 */
private val RankColors = listOf(
    Color(0xFFE53935),
    Color(0xFFF57C00),
    Color(0xFFFBC02D)
)

/**
 * 打开站内详情页。正文已随列表接口返回，详情页直接渲染，
 * 不加载知乎网页——知乎对非官方客户端返回 403，只会看到登录墙。
 */
private fun openInApp(context: Context, item: ZhihuHotItem) {
    ARouterUtils.build(ARouterConstants.ACTIVITY_ZHIHU_DETAIL)
        .withSerializable(ZhihuDetailActivity.KEY_ITEM, item)
        .navigation(context)
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ZhihuHotView(viewModel: ZhihuHotViewModel) {
    val hotList by viewModel.hotLiveData.observeAsState(emptyList())
    val refreshing by viewModel.refreshingLiveData.observeAsState(false)
    val context = LocalContext.current

    val pullState = rememberPullRefreshState(
        refreshing = refreshing,
        onRefresh = { viewModel.refresh() }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pullRefresh(pullState)
    ) {
        when {
            // 首次加载只留下拉刷新那一个指示器，不要再叠一个居中的圈
            hotList.isEmpty() && refreshing -> Unit

            hotList.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = "热搜加载失败", color = Color.Gray)
                    Text(
                        text = "下拉重试",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(hotList, key = { _, item -> item.rank }) { _, item ->
                        ZhihuHotItemRow(item = item, onClick = {
                            openInApp(context, item)
                        })
                        Divider(
                            modifier = Modifier.padding(start = 48.dp),
                            thickness = 0.5.dp,
                            color = Color(0xFFC4BEAE)
                        )
                    }
                }
            }
        }
        PullRefreshIndicator(refreshing, pullState, Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun ZhihuHotItemRow(item: ZhihuHotItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = item.rank.toString(),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = RankColors.getOrNull(item.rank - 1) ?: Color(0xFF757575),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(24.dp)
                .padding(top = 2.dp, end = 8.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (item.detailText.isNotEmpty()) {
                Text(
                    text = item.detailText,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        if (item.trend == "up" || item.trend == "down") {
            Text(
                text = if (item.trend == "up") "▲" else "▼",
                fontSize = 12.sp,
                color = if (item.trend == "up") Color(0xFFE53935) else Color(0xFF43A047),
                modifier = Modifier.padding(start = 8.dp, top = 2.dp)
            )
        }
    }
}
