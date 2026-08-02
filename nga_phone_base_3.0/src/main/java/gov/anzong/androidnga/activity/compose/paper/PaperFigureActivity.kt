package gov.anzong.androidnga.activity.compose.paper

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.alibaba.android.arouter.facade.annotation.Route
import com.justwen.androidnga.ui.compose.BaseComposeActivity
import com.justwen.androidnga.ui.compose.widget.TopAppBarData
import gov.anzong.androidnga.arouter.ARouterConstants

/**
 * 单张论文插图的查看页。整页只有这一张图，可双指缩放、拖动。
 */
@Route(path = ARouterConstants.ACTIVITY_PAPER_FIGURE)
class PaperFigureActivity : BaseComposeActivity() {

    private val url: String by lazy { intent.getStringExtra(KEY_URL).orEmpty() }

    private val figureIndex: Int by lazy { intent.getIntExtra(KEY_INDEX, 0) }

    override fun getTopAppBarData(): TopAppBarData {
        val title = if (figureIndex > 0) "图 $figureIndex" else "论文插图"
        val topAppBarData = TopAppBarData(title = title)
        topAppBarData.navigationIconAction = { finish() }
        return topAppBarData
    }

    @Composable
    override fun ContentView() {
        FigureContent(url)
    }

    companion object {
        const val KEY_URL = "figure_url"
        const val KEY_INDEX = "figure_index"
    }
}

@Composable
private fun FigureContent(url: String) {
    if (url.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "图片地址无效", color = Color.Gray)
        }
        return
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val painter = rememberAsyncImagePainter(url)
    val state = painter.state

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 论文插图基本都是白底黑线，深色主题下直接看会糊，垫一层白底
            .background(Color.White)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    // 原始比例时不允许拖动，避免图跑出屏幕找不回来
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Image 必须始终参与绘制，Coil 才会真的发起请求
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                )
        )
        when (state) {
            is AsyncImagePainter.State.Loading -> Text(
                text = "加载中…",
                fontSize = 14.sp,
                color = Color.Gray
            )

            is AsyncImagePainter.State.Error -> Text(
                text = "图片加载失败\n$url",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(24.dp)
            )

            else -> Unit
        }
    }
}
