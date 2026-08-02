package gov.anzong.androidnga.activity.compose.paper

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import gov.anzong.androidnga.activity.compose.paper.data.LatexImage
import gov.anzong.androidnga.activity.compose.paper.data.PaperBlock
import gov.anzong.androidnga.activity.compose.paper.data.PaperItem
import gov.anzong.androidnga.arouter.ARouterConstants
import gov.anzong.androidnga.base.util.ToastUtils
import sp.phone.util.ARouterUtils
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** 译文的颜色，和英文原文区分开 */
private val TranslationColor = Color(0xFF1A6FB5)

/**
 * 论文详情：题录 + 全文。
 *
 * 正文按句子排列，每句下面跟一条中文翻译；公式和插图以图片呈现，
 * 和 PDF 里看到的一致，不会有转文本导致的乱码。
 * 整页包在 SelectionContainer 里，长按可选词复制。
 */
@Composable
fun PaperDetailView(paper: PaperItem?, viewModel: PaperDetailViewModel) {
    if (paper == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "内容已失效", color = Color.Gray)
        }
        return
    }

    val context = LocalContext.current
    val blocks by viewModel.blocksLiveData.observeAsState(emptyList())
    val loading by viewModel.loadingLiveData.observeAsState(false)
    val failed by viewModel.failedLiveData.observeAsState(false)
    val translations by viewModel.translationLiveData.observeAsState(emptyMap())
    val translateOn by viewModel.translateEnabledLiveData.observeAsState(true)

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val showBackToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 3 }
    }

    // 滚到哪翻到哪：可见范围变化后翻译这一屏的句子
    TranslateVisible(listState, blocks, translateOn, viewModel)

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // 文字包在 SelectionContainer 里才能长按选词复制；
            // 图片不要包进去——SelectionContainer 会让内部 Image 不显示
            item { SelectionContainer { PaperHeader(paper) } }

            if (loading && blocks.isEmpty()) {
                item { LoadingRow("正在加载全文…") }
            }

            itemsIndexed(blocks) { _, block ->
                when (block) {
                    is PaperBlock.Heading -> SelectionContainer { HeadingBlock(block) }

                    is PaperBlock.Paragraph -> SelectionContainer {
                        ParagraphBlock(
                            block = block,
                            translations = translations,
                            translateOn = translateOn
                        )
                    }

                    // 公式和插图是图片，放在选择容器外
                    is PaperBlock.Formula -> FormulaBlock(block)
                    is PaperBlock.Image -> FigureLink(block) {
                        openFigure(context, block)
                    }
                    is PaperBlock.Table -> SelectionContainer { TableBlock(block) }
                }
            }

            if (failed && !loading) {
                item { FailedBlock { viewModel.retry(paper.arxivId) } }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(
                    onClick = { openInBrowser(context, paper.absUrl) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "在 arXiv 查看原文", fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

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
 * 监听可见范围，把这一屏的句子送去翻译。
 * debounce 一下，快速滑动时不必为掠过的内容发请求。
 */
@Composable
private fun TranslateVisible(
    listState: LazyListState,
    blocks: List<PaperBlock>,
    translateOn: Boolean,
    viewModel: PaperDetailViewModel
) {
    LaunchedEffect(blocks, translateOn) {
        if (!translateOn || blocks.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo.visibleItemsInfo
            if (info.isEmpty()) null else info.first().index to info.last().index
        }
            .distinctUntilChanged()
            .debounce(250)
            .collect { range ->
                if (range == null) return@collect
                val (first, last) = range
                // item 0 是题录区，正文块从 1 开始，往后多取一屏预热
                val from = (first - 1).coerceAtLeast(0)
                val to = (last + 3).coerceAtMost(blocks.size - 1)
                if (from > to) return@collect
                val sentences = blocks.subList(from, to + 1)
                    .filterIsInstance<PaperBlock.Paragraph>()
                    .flatMap { it.sentences }
                viewModel.translateVisible(sentences)
            }
    }
}

/** 题录区：标题、作者、日期分类 */
@Composable
private fun PaperHeader(paper: PaperItem) {
    Column {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = paper.title,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 31.sp,
            color = MaterialTheme.colors.onSurface
        )
        if (paper.authors.isNotEmpty()) {
            Text(
                text = paper.authors.joinToString("、"),
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        val meta = buildList {
            if (paper.published.isNotEmpty()) add("📅 ${paper.published}")
            if (paper.category.isNotEmpty()) add(paper.category)
            add("arXiv:${paper.arxivId}")
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            meta.forEach { Text(text = it, fontSize = 12.sp, color = Color.Gray) }
        }
        Divider(
            modifier = Modifier.padding(vertical = 14.dp),
            thickness = 0.5.dp,
            color = Color(0xFFC4BEAE)
        )
    }
}

/** 一段正文：逐句显示，每句下面跟译文 */
@Composable
private fun ParagraphBlock(
    block: PaperBlock.Paragraph,
    translations: Map<String, String>,
    translateOn: Boolean
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        block.sentences.forEach { sentence ->
            Text(
                text = sentence,
                fontSize = 16.sp,
                lineHeight = 26.sp,
                color = MaterialTheme.colors.onSurface
            )
            if (translateOn) {
                val zh = translations[sentence]
                Text(
                    text = zh ?: "翻译中…",
                    fontSize = 14.sp,
                    lineHeight = 23.sp,
                    color = if (zh != null) TranslationColor else Color.Gray,
                    modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * 独立公式。渲染成图片，效果和 PDF 一致。
 * 加载不出来时退回显示 LaTeX 源码，至少不丢信息。
 */
@Composable
private fun FormulaBlock(block: PaperBlock.Formula) {
    val url = remember(block.latex) { LatexImage.blockUrl(block.latex) }
    val painter = rememberAsyncImagePainter(url)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        // Image 必须始终参与绘制，AsyncImagePainter 才会真的发起请求；
        // 藏在分支里不画的话状态会永远停在 Loading
        Image(
            painter = painter,
            contentDescription = block.latex,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 120.dp)
        )
        // 加载失败时把 LaTeX 源码盖在上面，至少不丢信息
        if (painter.state is AsyncImagePainter.State.Error) {
            SelectionContainer {
                Text(
                    text = block.latex,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun HeadingBlock(block: PaperBlock.Heading) {
    val size = when (block.level) {
        2 -> 19.sp
        3 -> 17.sp
        else -> 15.sp
    }
    Text(
        text = block.text,
        fontSize = size,
        fontWeight = FontWeight.Bold,
        lineHeight = size * 1.4f,
        color = MaterialTheme.colors.onSurface,
        modifier = Modifier.padding(top = 22.dp, bottom = 6.dp)
    )
}

/**
 * 论文插图在正文里只显示成一个「图 N」的链接，
 * 点开在单独页面看大图——插图在正文里缩到屏宽基本看不清细节。
 */
@Composable
private fun FigureLink(block: PaperBlock.Image, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x11808080))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "🖼", fontSize = 16.sp)
        Text(
            text = "图 ${block.index}",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = TranslationColor,
            modifier = Modifier.padding(start = 8.dp)
        )
        Text(
            text = "点击查看",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

/**
 * 表格。论文表格列多，压到屏宽会挤成一团，
 * 所以用等宽字体保持列对齐并允许横向滚动。
 */
@Composable
private fun TableBlock(block: PaperBlock.Table) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0x11808080))
            .horizontalScroll(rememberScrollState())
            .padding(10.dp)
    ) {
        Column {
            block.rows.forEach { row ->
                Text(
                    text = row,
                    fontSize = 13.sp,
                    lineHeight = 22.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colors.onSurface
                )
            }
        }
    }
}

@Composable
private fun FailedBlock(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "没有抓到全文", fontSize = 14.sp, color = Color.Gray)
        Text(
            text = "这篇论文可能还没有 HTML 版本",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 4.dp)
        )
        OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
            Text(text = "重试", fontSize = 13.sp)
        }
    }
}

@Composable
private fun LoadingRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
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

/** 在单独页面打开这张插图 */
private fun openFigure(context: Context, block: PaperBlock.Image) {
    ARouterUtils.build(ARouterConstants.ACTIVITY_PAPER_FIGURE)
        .withString(PaperFigureActivity.KEY_URL, block.url)
        .withInt(PaperFigureActivity.KEY_INDEX, block.index)
        .navigation(context)
}

private fun openInBrowser(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        ToastUtils.error("没有找到可用的浏览器")
    }
}
