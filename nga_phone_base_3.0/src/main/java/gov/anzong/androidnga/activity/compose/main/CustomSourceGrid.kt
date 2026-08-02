package gov.anzong.androidnga.activity.compose.main

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alibaba.android.arouter.launcher.ARouter
import gov.anzong.androidnga.R

/**
 * 自定义内容源入口。不是 NGA 板块，点开对应的是 App 内其它页面
 * （如知乎热搜）。以后要加内容源就在列表里多放一个。
 */
data class CustomSource(
    val name: String,
    /** ARouter 路由路径 */
    val route: String,
    /** 入口图标，不给就用默认板块图标 */
    @DrawableRes val icon: Int = R.drawable.default_board_icon
)

/**
 * 内容源九宫格，样式和板块网格保持一致。
 */
@Composable
fun CustomSourceGridView(sources: List<CustomSource>) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        items(sources.size) { index ->
            val source = sources[index]
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        ARouter.getInstance().build(source.route).navigation()
                    }
                    .padding(vertical = 16.dp)
            ) {
                Image(
                    painter = painterResource(id = source.icon),
                    contentDescription = source.name,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Text(
                    text = source.name,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
