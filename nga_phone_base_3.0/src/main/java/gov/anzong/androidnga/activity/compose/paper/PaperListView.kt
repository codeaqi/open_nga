package gov.anzong.androidnga.activity.compose.paper

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
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
import gov.anzong.androidnga.activity.compose.paper.data.PaperItem
import gov.anzong.androidnga.arouter.ARouterConstants
import sp.phone.util.ARouterUtils

/**
 * 论文列表。数据来自 arXiv 官方 API，清单在 PaperRepository 里维护。
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PaperListView(viewModel: PaperListViewModel) {
    val papers by viewModel.paperLiveData.observeAsState(emptyList())
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
            // 首次加载只留下拉刷新那一个指示器
            papers.isEmpty() && refreshing -> Unit

            papers.isEmpty() -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(text = "论文加载失败", color = Color.Gray)
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
                    items(papers, key = { it.arxivId }) { paper ->
                        PaperRow(paper) { openDetail(context, paper) }
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
private fun PaperRow(paper: PaperItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = paper.index.toString(),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF757575),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(24.dp)
                .padding(top = 2.dp, end = 8.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = paper.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 24.sp,
                color = MaterialTheme.colors.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (paper.authors.isNotEmpty()) {
                Text(
                    text = paper.authorSummary(),
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            val meta = buildList {
                if (paper.published.isNotEmpty()) add(paper.published)
                if (paper.category.isNotEmpty()) add(paper.category)
            }
            if (meta.isNotEmpty()) {
                Text(
                    text = meta.joinToString("  ·  "),
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

private fun openDetail(context: Context, paper: PaperItem) {
    ARouterUtils.build(ARouterConstants.ACTIVITY_PAPER_DETAIL)
        .withSerializable(PaperDetailActivity.KEY_PAPER, paper)
        .navigation(context)
}
