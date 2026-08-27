package com.justwen.androidnga.ui.compose.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.systemuicontroller.rememberSystemUiController

@Composable
fun FloatingActionButton(fabClick: (() -> Unit)? = null) {
    if (fabClick != null) {
        FloatingActionButton(
            modifier = Modifier.navigationBarsPadding(),
            backgroundColor = MaterialTheme.colors.primary,
            onClick = { fabClick.invoke() }) {
            Icon(Icons.Default.Add, tint = Color.White, contentDescription = "Add")
        }
    }
}

@Composable
fun OptionActionMenu(optionActions: List<OptionMenuData>? = null) {
    if (optionActions == null) {
        return
    }

    val showItems = optionActions.filter { it.type == OptionMenuData.OPTION_MENU_TYPE_ALWAYS_SHOW }
    val hideItems = optionActions.filter { it.type == OptionMenuData.OPTION_MENU_TYPE_HIDDEN }

    Row(verticalAlignment = Alignment.CenterVertically) {
        showItems.forEach {
            IconButton(onClick = {
                it.action()
            }) {
                Icon(
                    painter = painterResource(it.icon!!),
                    contentDescription = "",
                    tint = Color.White
                )
            }

        }
        if (hideItems.isEmpty()) {
            return@Row
        }
        var expanded by remember { mutableStateOf(false) }
        IconButton(onClick = { expanded = !expanded }) {
            Icon(imageVector = Icons.Default.MoreVert, contentDescription = "", tint = Color.White)
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                hideItems.forEach(action = {
                    DropdownMenuItem(onClick = {
                        expanded = false
                        it.action()
                    }) {
                        Text(text = it.title!!)
                    }
                })
            }
        }

    }

}

@Composable
fun TopAppBarEx(
    topAppBarData: TopAppBarData,
) {
    val paddingValues = WindowInsets.statusBars.asPaddingValues()
    val top = paddingValues.calculateTopPadding()
    val pxValue = with(LocalDensity.current) { top.toPx() }

    TopAppBar(
        backgroundColor = MaterialTheme.colors.primary,
        windowInsets = WindowInsets(0, pxValue.toInt(), 0, 0),
        title = {
            if (topAppBarData.customTopBar == null) {
                AutoSizeTitle(text = topAppBarData.title)
            } else {
                topAppBarData.customTopBar!!.invoke()
            }
        },
        navigationIcon = {
            IconButton(onClick = {
                topAppBarData.navigationIconAction?.invoke()
            }) {
                val customIcon = topAppBarData.navigationIcon
                if (customIcon != null) {
                    customIcon.invoke()
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        tint = Color.White,
                        contentDescription = "Localized description"
                    )
                }
            }
        },
        actions = {
            OptionActionMenu(optionActions = topAppBarData.optionMenuData)
        })
}

/** 标题的默认字号，和 Material 的 h6 一致 */
private val TitleFontSize = 20.sp

/** 自动缩小的下限，再小就看不清了，到底了就让尾部省略 */
private val TitleFontSizeMin = 12.sp

/** 每次缩小的比例 */
private const val TITLE_SHRINK_STEP = 0.9f

/**
 * 标题栏文字：装不下时逐级缩小字号，而不是换行。
 *
 * TopAppBar 的高度是固定的，原来的 Text 没限制行数，长标题会换到第二行、
 * 而第二行整个在标题栏外面，被下方内容盖掉——用户看到的是被切掉一半的字。
 * 系统字体放大时更容易触发（1.45 倍下十来个汉字就换行了）。
 *
 * 先隐藏再测量：字号还在往下调的过程中不绘制，否则会看到文字跳几帧才稳定。
 */
@Composable
private fun AutoSizeTitle(text: String) {
    // 换标题要重新从最大字号试起，否则上一个长标题缩小后的字号会留给下一个短标题
    var fontSize by remember(text) { mutableStateOf(TitleFontSize) }
    var readyToDraw by remember(text) { mutableStateOf(false) }
    Text(
        text = text,
        color = Color.White,
        fontSize = fontSize,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.drawWithContent {
            if (readyToDraw) {
                drawContent()
            }
        },
        onTextLayout = { result ->
            if (result.didOverflowWidth && fontSize > TitleFontSizeMin) {
                fontSize = fontSize * TITLE_SHRINK_STEP
            } else {
                readyToDraw = true
            }
        }
    )
}

data class TopAppBarData(val title: String) {
    var navigationIconAction: (() -> Unit)? = null
    var optionMenuData: List<OptionMenuData>? = null
    var customTopBar: @Composable (() -> Unit)? = null

    /** 自定义左上角图标，为空时用默认的返回箭头 */
    var navigationIcon: @Composable (() -> Unit)? = null
}

data class OptionMenuData(
    val title: String? = null,
    val icon: Int? = null,
    val action: (() -> Unit),
    val type: Int = OPTION_MENU_TYPE_HIDDEN
) {
    companion object {
        const val OPTION_MENU_TYPE_HIDDEN = 1
        const val OPTION_MENU_TYPE_ALWAYS_SHOW = 2
    }
}

@Preview()
@Composable
fun ScaffoldApp(
    topAppBarData: TopAppBarData = TopAppBarData("App"),
    fabClick: (() -> Unit)? = null,
    bottomBar: @Composable (() -> Unit)? = null,
    showTopBar: Boolean = true,
    appContent: @Composable (() -> Unit)? = null,
) {
    // 改状态栏是副作用，不能裸写在组合里——那样每重组一次就会去动一次窗口属性。
    // 放进 SideEffect，只在组合成功提交后跑。
    val systemUiController = rememberSystemUiController()
    val statusBarColor = MaterialTheme.colors.primary
    SideEffect {
        systemUiController.setStatusBarColor(statusBarColor, false)
    }
    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBarEx(
                    topAppBarData = topAppBarData
                )
            }
        },
        bottomBar = {
            bottomBar?.invoke()
        },
        floatingActionButton = {
            FloatingActionButton(fabClick = fabClick)
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            appContent?.invoke()
        }
    }
}