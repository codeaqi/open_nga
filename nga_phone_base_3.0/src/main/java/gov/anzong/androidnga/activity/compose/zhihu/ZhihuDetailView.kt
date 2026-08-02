package gov.anzong.androidnga.activity.compose.zhihu

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import gov.anzong.androidnga.activity.compose.zhihu.data.ZhihuAnswer
import gov.anzong.androidnga.activity.compose.zhihu.data.ZhihuBlock
import gov.anzong.androidnga.activity.compose.zhihu.data.ZhihuComment
import gov.anzong.androidnga.activity.compose.zhihu.data.ZhihuHotItem
import gov.anzong.androidnga.base.util.ToastUtils
import kotlinx.coroutines.launch

/** 折叠状态下正文最多显示的行数 */
private const val COLLAPSED_MAX_LINES = 6

/** 问题描述折叠时的行数 */
private const val DETAIL_MAX_LINES = 4

/** 作者名统一用这个色，和正文的默认字色拉开差别 */
private val AuthorNameColor = Color(0xFF1A6FB5)

/** 头像色块的备选色，按名字取模，保证同一个人颜色稳定 */
private val AvatarColors = listOf(
    Color(0xFF5B8FF9), Color(0xFF5AD8A6), Color(0xFF5D7092),
    Color(0xFFF6BD16), Color(0xFFE86452), Color(0xFF6DC8EC),
    Color(0xFF945FB9), Color(0xFFFF9845)
)

/**
 * 用户名首字做的圆形头像块。知乎接口没给头像 URL，
 * 用色块代替也足够把「谁说的」和「说了什么」区分开。
 */
@Composable
private fun AvatarBadge(name: String, size: Int = 28) {
    val color = remember(name) {
        AvatarColors[(name.hashCode().let { if (it < 0) -it else it }) % AvatarColors.size]
    }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.take(1),
            fontSize = (size / 2).sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

/**
 * 知乎热搜详情：标题 + 热度 + 问题描述 + 网友回答（可折叠、可看评论、可加载更多）。
 *
 * 回答由 ZhihuAnswerFetcher 用隐藏 WebView 抓回来后原生渲染，
 * 页面上不出现任何知乎的 UI、引流和广告。
 */
@Composable
fun ZhihuDetailView(hotItem: ZhihuHotItem?, viewModel: ZhihuDetailViewModel) {
    if (hotItem == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "内容已失效", color = Color.Gray)
        }
        return
    }

    val context = LocalContext.current
    val answers by viewModel.answersLiveData.observeAsState(emptyList())
    val loading by viewModel.loadingLiveData.observeAsState(false)
    val loadingMore by viewModel.loadingMoreLiveData.observeAsState(false)
    val isEmpty by viewModel.emptyLiveData.observeAsState(false)
    val fetchedDetail by viewModel.detailLiveData.observeAsState(emptyList())
    val hasMore by viewModel.hasMoreLiveData.observeAsState(false)
    val total by viewModel.totalLiveData.observeAsState(0)
    val expanded by viewModel.expandedLiveData.observeAsState(emptySet())
    val comments by viewModel.commentsLiveData.observeAsState(emptyMap())
    val commentLoading by viewModel.commentLoadingLiveData.observeAsState(emptySet())
    val commentShown by viewModel.commentShownLiveData.observeAsState(emptySet())

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 问题描述默认折叠，长文不至于把回答挤到屏幕外
    var detailExpanded by rememberSaveable { mutableStateOf(false) }

    // 滑过一屏后才显示回到顶部按钮
    val showBackToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 2 }
    }

    // 网页里抽到的描述带配图且更完整，没抓到就退回列表接口给的纯文本
    val detailBlocks = fetchedDetail.ifEmpty {
        if (hotItem.content.isNotEmpty()) {
            listOf(ZhihuBlock.TextBlock(hotItem.content))
        } else {
            emptyList()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = hotItem.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 30.sp,
                    color = MaterialTheme.colors.onSurface
                )
                MetaRow(hotItem)
                DetailSection(
                    blocks = detailBlocks,
                    expanded = detailExpanded,
                    onToggle = { detailExpanded = !detailExpanded }
                )
                Divider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    thickness = 0.5.dp,
                    color = Color(0xFFC4BEAE)
                )
            }

            if (loading && answers.isEmpty()) {
                item { LoadingBlock("正在加载回答…") }
            }

            if (answers.isNotEmpty()) {
                item {
                    Text(
                        text = if (total > 0) "$total 个回答" else "${answers.size} 个回答",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colors.onSurface,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                itemsIndexed(answers, key = { i, a -> a.id.ifEmpty { "idx$i" } }) { index, answer ->
                    AnswerCard(
                        answer = answer,
                        expanded = answer.id in expanded,
                        onToggleExpand = { viewModel.toggleExpand(answer.id) },
                        commentsShown = answer.id in commentShown,
                        commentsLoading = answer.id in commentLoading,
                        comments = comments[answer.id],
                        onToggleComments = { viewModel.toggleComments(answer.id) }
                    )
                    if (index < answers.size - 1) {
                        Divider(thickness = 0.5.dp, color = Color(0xFFC4BEAE))
                    }
                }
            }

            // 加载更多
            if (answers.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    when {
                        loadingMore -> LoadingBlock("正在加载更多…")
                        hasMore -> Button(
                            onClick = { viewModel.loadMore() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "加载更多回答", fontSize = 15.sp)
                        }

                        else -> Text(
                            text = "— 没有更多了 —",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            if (isEmpty && !loading) {
                item { EmptyBlock(onRetry = { viewModel.refresh(hotItem.url) }) }
            }

            item {
                if (hotItem.url.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { openInBrowser(context, hotItem.url) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = "用浏览器打开原帖", fontSize = 14.sp)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // 回到顶部
        AnimatedVisibility(
            visible = showBackToTop,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp)
        ) {
            FloatingActionButton(
                onClick = { scope.launch { listState.scrollToItem(0) } },
                backgroundColor = MaterialTheme.colors.primary,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowUp,
                    contentDescription = "回到顶部",
                    tint = Color.White
                )
            }
        }
    }
}

/**
 * 问题描述区。默认折叠成几行，内容确实被截断时才显示展开按钮。
 * 折叠态只渲染文字；展开后按原顺序显示完整图文。
 */
@Composable
private fun DetailSection(
    blocks: List<ZhihuBlock>,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    if (blocks.isEmpty()) {
        return
    }
    // 折叠时是否真的有内容被藏起来：文字被截断，或者还有图片没显示
    var textTruncated by remember(blocks) { mutableStateOf(false) }
    val hasImage = blocks.any { it is ZhihuBlock.ImageBlock }
    val textCount = blocks.count { it is ZhihuBlock.TextBlock }

    if (expanded) {
        blocks.forEach { block ->
            when (block) {
                is ZhihuBlock.TextBlock -> Text(
                    text = block.text,
                    fontSize = 15.sp,
                    lineHeight = 25.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 12.dp)
                )

                is ZhihuBlock.ImageBlock -> ContentImage(block.url)
            }
        }
    } else {
        val firstText = blocks.filterIsInstance<ZhihuBlock.TextBlock>()
            .firstOrNull()?.text.orEmpty()
        if (firstText.isNotEmpty()) {
            Text(
                text = firstText,
                fontSize = 15.sp,
                lineHeight = 25.sp,
                color = Color.Gray,
                maxLines = DETAIL_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { textTruncated = it.hasVisualOverflow },
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clickable(onClick = onToggle)
            )
        }
    }

    val canToggle = textTruncated || hasImage || textCount > 1
    if (canToggle) {
        Text(
            text = if (expanded) "收起描述" else "展开描述",
            fontSize = 13.sp,
            color = Color(0xFF0084FF),
            modifier = Modifier
                .padding(top = 6.dp)
                .clickable(onClick = onToggle)
        )
    }
}

@Composable
private fun LoadingBlock(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            text = text,
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun EmptyBlock(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "没有抓到回答", fontSize = 14.sp, color = Color.Gray)
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Text(text = "重试", fontSize = 13.sp)
        }
    }
}

/** 一条回答：作者 + 赞同数 + 正文（可折叠）+ 评论区 */
@Composable
private fun AnswerCard(
    answer: ZhihuAnswer,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    commentsShown: Boolean,
    commentsLoading: Boolean,
    comments: List<ZhihuComment>?,
    onToggleComments: () -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        // 作者行：头像色块 + 名字，和正文明显区分开
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarBadge(answer.author)
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = answer.author,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = AuthorNameColor
                    )
                    if (answer.voteCount > 0) {
                        Text(
                            text = "▲ ${answer.voteCount}",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                if (answer.headline.isNotEmpty()) {
                    Text(
                        text = answer.headline,
                        fontSize = 11.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 折叠时只渲染第一段文字并限制行数，展开后按原顺序显示全部图文
        var truncated by remember(answer.id) { mutableStateOf(false) }
        if (!expanded) {
            val firstText = answer.blocks
                .filterIsInstance<ZhihuBlock.TextBlock>()
                .firstOrNull()?.text.orEmpty()
            Text(
                text = firstText,
                fontSize = 16.sp,
                lineHeight = 26.sp,
                color = MaterialTheme.colors.onSurface,
                maxLines = COLLAPSED_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { truncated = it.hasVisualOverflow },
                modifier = Modifier.clickable(onClick = onToggleExpand)
            )
        } else {
            answer.blocks.forEach { block ->
                when (block) {
                    is ZhihuBlock.TextBlock -> Text(
                        text = block.text,
                        fontSize = 16.sp,
                        lineHeight = 26.sp,
                        color = MaterialTheme.colors.onSurface,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    is ZhihuBlock.ImageBlock -> ContentImage(block.url)
                }
            }
        }

        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 内容没被截断（短回答且无图）就不显示展开入口
            val multiBlock = answer.blocks.size > 1
            if (truncated || multiBlock || expanded) {
                Text(
                    text = if (expanded) "收起" else "展开全文",
                    fontSize = 13.sp,
                    color = Color(0xFF0084FF),
                    modifier = Modifier.clickable(onClick = onToggleExpand)
                )
            }
            if (answer.commentCount > 0 && answer.id.isNotEmpty()) {
                Text(
                    text = if (commentsShown) {
                        "收起评论"
                    } else {
                        "${answer.commentCount} 条评论"
                    },
                    fontSize = 13.sp,
                    color = Color(0xFF0084FF),
                    modifier = Modifier.clickable(onClick = onToggleComments)
                )
            }
        }

        if (commentsShown) {
            CommentList(loading = commentsLoading, comments = comments)
        }
    }
}

/** 某条回答下的评论 */
@Composable
private fun CommentList(loading: Boolean, comments: List<ZhihuComment>?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x11808080))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        when {
            loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text(
                    text = "正在加载评论…",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }

            comments.isNullOrEmpty() -> Text(
                text = "没有抓到评论",
                fontSize = 13.sp,
                color = Color.Gray
            )

            else -> comments.forEachIndexed { index, c ->
                Row(modifier = Modifier.padding(vertical = 7.dp)) {
                    AvatarBadge(c.author, size = 22)
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = c.author,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AuthorNameColor
                            )
                            if (c.likeCount > 0) {
                                Text(
                                    text = "▲ ${c.likeCount}",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                        Text(
                            text = c.content,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            color = MaterialTheme.colors.onSurface,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                if (index < comments.size - 1) {
                    Divider(thickness = 0.5.dp, color = Color(0x22808080))
                }
            }
        }
    }
}

/**
 * 回答里的配图。
 *
 * 知乎图片必须带 Referer 才不会被反盗链拦成 403，所以这里显式加上；
 * 高度让 Coil 按原图比例自适应，不写死避免变形。
 */
@Composable
private fun ContentImage(url: String) {
    val context = LocalContext.current
    val request = remember(url) {
        ImageRequest.Builder(context)
            .data(url)
            .setHeader("Referer", "https://www.zhihu.com/")
            .setHeader("User-Agent", IMAGE_UA)
            .crossfade(true)
            .build()
    }
    Image(
        painter = rememberAsyncImagePainter(request),
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(4.dp))
    )
}

private const val IMAGE_UA =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

/** 热度、回答数、关注数，取不到的就不显示 */
@Composable
private fun MetaRow(item: ZhihuHotItem) {
    val metas = buildList {
        if (item.detailText.isNotEmpty()) add("🔥 ${item.detailText}")
        if (item.answerCount > 0) add("💬 ${item.answerCount} 回答")
        if (item.followerCount > 0) add("⭐ ${item.followerCount} 关注")
    }
    if (metas.isEmpty()) {
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        metas.forEach {
            Text(text = it, fontSize = 13.sp, color = Color.Gray)
        }
    }
}

private fun openInBrowser(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        ToastUtils.error("没有找到可用的浏览器")
    }
}
