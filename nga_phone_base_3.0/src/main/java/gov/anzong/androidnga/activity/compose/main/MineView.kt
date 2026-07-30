package gov.anzong.androidnga.activity.compose.main

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.justwent.androidnga.bu.UserManager
import gov.anzong.androidnga.R
import gov.anzong.androidnga.activity.compose.drawer.NavigationDrawerViewModel

private data class MineEntry(val title: String, val action: () -> Unit)

/**
 * 「我的」页：原先首页右上角溢出菜单的功能集中到这里。
 * 「搜索用户」不在此列——它是全局动作，仍留在顶栏图标位。
 */
@Composable
fun MineView(viewModel: NavigationDrawerViewModel, activity: Activity) {
    val entries = listOf(
        MineEntry("我的主题") { viewModel.startPostPage(activity, false) },
        MineEntry("我的回复") { viewModel.startPostPage(activity, true) },
        MineEntry("短消息") { viewModel.startMessagePage(activity) },
        MineEntry("隐藏夹") { viewModel.startHiddenBoardPage(activity) },
        MineEntry("设置") { viewModel.startSettingsPage(activity) },
        MineEntry("收藏夹") { viewModel.startFavoriteTopicPage(activity) },
        MineEntry("我的缓存") { viewModel.startCacheTopicPage(activity) },
    )

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { MineHeader(viewModel, activity) }
        items(entries.size) { index ->
            val entry = entries[index]
            Text(
                text = entry.title,
                fontSize = 16.sp,
                color = MaterialTheme.colors.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { entry.action() }
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun MineHeader(viewModel: NavigationDrawerViewModel, activity: Activity) {
    val userList by UserManager.getUserListLiveData().observeAsState(emptyList())
    val user = userList.firstOrNull()

    // 本页没有标题栏，头像卡片自己顶到状态栏下方
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.primary)
            .clickable {
                if (user != null) {
                    viewModel.startProfilePage(activity, user)
                } else {
                    viewModel.startLoginPage(activity)
                }
            }
            .statusBarsPadding()
            .padding(20.dp)
    ) {
        val avatarPainter: Painter
        val avatarColorFilter: ColorFilter?
        if (user?.avatarUrl?.isNotEmpty() == true) {
            avatarPainter = rememberAsyncImagePainter(
                model = user.avatarUrl,
                placeholder = painterResource(id = R.drawable.drawerdefaulticon),
            )
            avatarColorFilter = null
        } else {
            avatarPainter = painterResource(id = R.drawable.drawerdefaulticon)
            avatarColorFilter = ColorFilter.tint(Color.White)
        }
        Image(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(28.dp)),
            painter = avatarPainter,
            colorFilter = avatarColorFilter,
            contentDescription = ""
        )
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = user?.nickName ?: "未登录",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (user != null) "UID ${user.userId}" else "点击登录账号",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
