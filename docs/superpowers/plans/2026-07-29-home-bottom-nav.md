# 首页底部导航重构 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 NGA 首页从「抽屉 + 横向 pager」改成「抽屉 + 底部三 Tab（我的收藏 / 网事杂谈 / 我的）」，
溢出菜单迁入「我的」，自选股降为「我的收藏」的二级 Tab。

**Architecture:** 保留 `NavigationDrawerFragment` 作为宿主与 `ScaffoldApp` 外壳，在 Scaffold 的
`bottomBar` 挂 `NavigationBar`，内容区按选中 Tab 分发到三个独立 Composable。板块数据层去掉
`BoardType.STOCK` 伪版面，`ForumBoardViewModel` 改为按语义（bookmark / forum）取板块而非按下标。

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose（material 1.x + material3 1.3.2 混用，与现有代码一致），
ARouter，Android minSdk 30 / JVM 17。

## Global Constraints

- 抽屉（`ModalNavigationDrawer` 及其 6 个条目）保持原样，不删不改。
- 不改 `StockView` / `StockViewModel` 内部逻辑，只改挂载位置。
- 不改 `board_list.json`，不改 `BOARD_LOCAL_VERSION_CURRENT`（当前为 7）。
- 不新增 drawable 资源，不新增 Gradle 依赖。底部图标只用 `androidx.compose.material.icons.Icons.Default`
  **核心集**内的图标（`Star` / `Home` / `Person` 等）——`material-icons-extended` 不是本项目依赖，
  用了会编译失败。
- 新增 Kotlin 文件的包名沿用 `gov.anzong.androidnga.activity.compose.*`。
- 每个任务结束时 `./gradlew :nga_phone_base_3.0:compileDebugKotlin` 必须通过。
- 本项目无 Compose UI 测试基建，验证 = 编译通过 + 任务内列出的人工走查项。

---

### Task 1: 数据层去掉自选股伪版面

把自选股从 `boardList` 里摘出来，并给 `ForumBoardViewModel` 加按语义取板块的入口，
让后续 UI 不再依赖下标顺序。

**Files:**
- Modify: `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/data/BoardEntity.kt`
- Modify: `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/ForumBoardModel.kt`
- Modify: `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/ForumBoardViewModel.kt`
- Modify: `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/ForumBoardView.kt`

**Interfaces:**
- Consumes: 无（首个任务）
- Produces:
  - `ForumBoardViewModel.bookmarkBoard: BoardEntity` —— 收藏板块，非空
  - `ForumBoardViewModel.forumBoard: BoardEntity?` —— 第一个非收藏顶级板块，可能为 null
  - `BoardEntity.BoardType.STOCK` 不再存在

- [ ] **Step 1: 删除 `BoardType.STOCK` 常量**

在 `BoardEntity.kt` 中，把 `@IntDef` 改回不含 STOCK，并删掉常量本身：

```kotlin
    @IntDef(BoardType.BOARD, BoardType.ASSEMBLE, BoardType.GROUP, BoardType.BOOKMARK)
    annotation class BoardType {
        companion object {
            // 正常板块
            const val BOARD: Int = 0

            // 合集板块
            const val ASSEMBLE: Int = 1

            // 板块分类
            const val GROUP: Int = 2

            // 板块分类
            const val BOOKMARK: Int = 3

        }
    }
```

- [ ] **Step 2: 从 `ForumBoardModel` 摘掉 stock 占位**

删除 `companion object` 里的 `STOCK_BOARD_ID`、整个 `createStockBoard()` 方法，
以及 `init` 块末尾那行 `boardList.add(1, createStockBoard())` 和它上面的注释。
`init` 块改成：

```kotlin
    init {
        val context = ContextUtils.getContext()
        bookmarkBoard = ForumBoardRepository.loadBookmarkBoardList(context)
        localBoardList = ForumBoardRepository.loadLocalBoardList(context)
        boardList.add(bookmarkBoard)
        boardList.addAll(localBoardList)
        boardList.forEach {
            initBoardMap(it, null)
        }
        transferBookmarkBoards()
    }
```

注意：`companion object` 删空后整个 `companion object { }` 块也一并删除。

- [ ] **Step 3: 给 `ForumBoardViewModel` 加按语义取板块的属性**

在 `ForumBoardViewModel` 中，`getBoardData` 方法下方加入（`getBoardData` 本身保留不动，
其它调用方仍在用）：

```kotlin
    /** 收藏板块，始终存在 */
    val bookmarkBoard: BoardEntity
        get() = forumBoardModel.bookmarkBoard

    /** 第一个真实版面分类（当前数据下即「网事杂谈」），板块数据为空时为 null */
    val forumBoard: BoardEntity?
        get() = boardLiveData.value?.firstOrNull {
            it.type != BoardEntity.BoardType.BOOKMARK
        }
```

- [ ] **Step 4: 去掉 `ForumBoardContent` 的 STOCK 分支**

在 `ForumBoardView.kt` 的 `ForumBoardContent` 中删掉第一个分支，改为：

```kotlin
    val boardData = forumBoardViewModel.getBoardData(index)
    if (boardData.type == BoardEntity.BoardType.BOOKMARK) {
        ForumBoardBookmarkContent(boardData, forumBoardViewModel)
    } else {
```

同时删掉 `ForumBoardContent` 的 `stockViewModel: StockViewModel` 参数，以及文件顶部
`import gov.anzong.androidnga.activity.compose.stock.StockView` 与
`import gov.anzong.androidnga.activity.compose.stock.StockViewModel` 两行。

`ForumBoardView` 这个顶层 Composable 里对 `ForumBoardContent(it, forumBoardViewModel, stockViewModel)`
的调用同步改为 `ForumBoardContent(it, forumBoardViewModel)`，并删掉 `ForumBoardView` 自己的
`stockViewModel` 参数。此时 `NavigationDrawerFragment` 对 `ForumBoardView(forumBoardViewModel, stockViewModel)`
的调用会编译失败——这是预期的，Task 4 会把这个调用整个换掉。为让本任务可独立编译，
把 `NavigationDrawerFragment` 第 358 行临时改为 `ForumBoardView(forumBoardViewModel)`。

- [ ] **Step 5: 编译验证**

Run: `./gradlew :nga_phone_base_3.0:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。若报 `Unresolved reference: STOCK` 说明还有遗漏引用，按报错位置清理。

- [ ] **Step 6: 人工走查**

安装运行，确认顶部 tab 变成「我的收藏 / 网事杂谈」两个（自选股 tab 消失），两个 tab 内容都正常。
此时自选股暂时无入口，属于中间态，Task 2 会恢复。

- [ ] **Step 7: Commit**

```bash
git add nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/ nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/drawer/NavigationDrawerFragment.kt
git commit -m "refactor: 移除自选股伪版面，板块改为按语义获取"
```

---

### Task 2: 我的收藏页（版面 | 自选股 二级 Tab）

**Files:**
- Create: `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/main/BookmarkTabView.kt`
- Modify: `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/ForumBoardView.kt`

**Interfaces:**
- Consumes: `ForumBoardViewModel.bookmarkBoard`（Task 1）、现有 `ForumBoardBookmarkContent`、现有 `StockView`
- Produces: `BookmarkTabView(forumBoardViewModel: ForumBoardViewModel, stockViewModel: StockViewModel)`

- [ ] **Step 1: 收藏为空时显示引导文案**

修改 `ForumBoardView.kt` 的 `ForumBoardBookmarkContent`，在 `LazyVerticalGrid` 外层加空态判断。
把该函数整体替换为：

```kotlin
@Composable
fun ForumBoardBookmarkContent(bookmark: BoardEntity, forumBoardViewModel: ForumBoardViewModel) {
    val bookmarkSize by forumBoardViewModel.bookmarkSizeLiveData.observeAsState()
    val maxColumn = 3

    Column (Modifier.fillMaxSize()) {

        if (UserManager.getUserList().size == 1) {
            Text(modifier = Modifier.padding(8.dp), text = "建议登录多个账号，可有效改善跳转系统浏览器问题")
        }

        if (bookmarkSize == null || bookmarkSize == 0) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "还没有收藏的版面\n在版面列表长按，或用左上角菜单的「添加版面ID」添加",
                    textAlign = TextAlign.Center,
                    color = Color.Gray
                )
            }
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(maxColumn),
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 8.dp)
        ) {
            items(bookmarkSize!!) { index ->
                ForumBoardGridItemView(bookmark.children!![index], forumBoardViewModel)
            }
            item(span = { GridItemSpan(maxColumn) }) {
                val paddingValues = WindowInsets.navigationBars.asPaddingValues()
                Spacer(modifier = Modifier.height(paddingValues.calculateBottomPadding()))
            }
        }
    }
}
```

在该文件 import 区补上（若已存在则跳过）：

```kotlin
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.style.TextAlign
```

- [ ] **Step 2: 新建 `BookmarkTabView.kt`**

```kotlin
package gov.anzong.androidnga.activity.compose.main

import androidx.compose.runtime.Composable
import com.justwen.androidnga.ui.compose.widget.TabLayoutWithPager
import gov.anzong.androidnga.activity.compose.board.ForumBoardBookmarkContent
import gov.anzong.androidnga.activity.compose.board.ForumBoardViewModel
import gov.anzong.androidnga.activity.compose.stock.StockView
import gov.anzong.androidnga.activity.compose.stock.StockViewModel

/**
 * 「我的收藏」页：二级 Tab，版面收藏与自选股。
 * 自选股不是版面，所以不进板块数据，只在这里与收藏并列展示。
 */
@Composable
fun BookmarkTabView(
    forumBoardViewModel: ForumBoardViewModel,
    stockViewModel: StockViewModel
) {
    TabLayoutWithPager(tabs = listOf("版面", "自选股"), initialPage = 0, fixed = true) { index ->
        if (index == 0) {
            ForumBoardBookmarkContent(forumBoardViewModel.bookmarkBoard, forumBoardViewModel)
        } else {
            StockView(stockViewModel)
        }
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `./gradlew :nga_phone_base_3.0:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/main/BookmarkTabView.kt nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/ForumBoardView.kt
git commit -m "feat: 我的收藏页增加版面/自选股二级 Tab"
```

---

### Task 3: 「我的」页

**Files:**
- Create: `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/main/MineView.kt`

**Interfaces:**
- Consumes: `NavigationDrawerViewModel` 已有的 `startPostPage` / `startCacheTopicPage` /
  `startMessagePage` / `startFavoriteTopicPage` / `startSettingsPage` / `startProfilePage` / `startLoginPage`
- Produces: `MineView(viewModel: NavigationDrawerViewModel, activity: Activity)`

- [ ] **Step 1: 新建 `MineView.kt`**

复用 `NavigationDrawerViewModel` 的跳转方法，不新写任何导航逻辑。

```kotlin
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
        MineEntry("我的缓存") { viewModel.startCacheTopicPage(activity) },
        MineEntry("短消息") { viewModel.startMessagePage(activity) },
        MineEntry("收藏夹") { viewModel.startFavoriteTopicPage(activity) },
        MineEntry("设置") { viewModel.startSettingsPage(activity) },
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
```

注意：头像区显示的是 `UserManager.getUserListLiveData()` 的第一个账号，不做多账号切换——
切换账号仍在抽屉里（抽屉头部有 AnimatedContent + 切换按钮），本页不重复实现。

- [ ] **Step 2: 编译验证**

Run: `./gradlew :nga_phone_base_3.0:compileDebugKotlin`
Expected: BUILD SUCCESSFUL。
若报 `User` 类型不匹配，检查 `startProfilePage(activity: Activity, user: User)` 的第二参数
类型是 `sp.phone.common.User`，与 `UserManager.getUserListLiveData()` 的元素类型一致。

- [ ] **Step 3: Commit**

```bash
git add nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/main/MineView.kt
git commit -m "feat: 新增「我的」页，承接原溢出菜单入口"
```

---

### Task 4: 底部导航壳，接线到 Fragment

把三个页面装进 `NavigationBar`，并清掉顶栏溢出菜单。这是让全部改动真正生效的任务。

**Files:**
- Create: `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/main/MainTab.kt`
- Modify: `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/drawer/NavigationDrawerFragment.kt`
- Modify: `lib_base_ui_compose/src/main/java/com/justwen/androidnga/ui/compose/widget/ScaffoldApp.kt`

**Interfaces:**
- Consumes: `BookmarkTabView`（Task 2）、`MineView`（Task 3）、`ForumBoardViewModel.forumBoard`（Task 1）
- Produces: `MainTab` 枚举；`ScaffoldApp` 新增可选 `bottomBar` 参数

- [ ] **Step 1: 给 `ScaffoldApp` 加 `bottomBar` 参数**

`ScaffoldApp` 目前没有 bottomBar。在 `ScaffoldApp.kt` 中把该函数改为：

```kotlin
@Preview()
@Composable
fun ScaffoldApp(
    topAppBarData: TopAppBarData = TopAppBarData("App"),
    fabClick: (() -> Unit)? = null,
    bottomBar: @Composable (() -> Unit)? = null,
    appContent: @Composable (() -> Unit)? = null,
) {
    rememberSystemUiController().run {
        setStatusBarColor(MaterialTheme.colors.primary, false)
    }
    Scaffold(
        topBar = {
            TopAppBarEx(
                topAppBarData = topAppBarData
            )
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
```

参数是可选的且加在 `appContent` 之前——现有调用方全部用具名参数或尾随 lambda 传 `appContent`，
不受影响。

- [ ] **Step 2: 新建 `MainTab.kt`**

图标只用 material 核心集内的（`Star` / `Home` / `Person`），`Forum` 在 extended 包里，本项目没有该依赖。

```kotlin
package gov.anzong.androidnga.activity.compose.main

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 首页底部导航的三个 Tab。
 * [title] 为 null 表示标题取自板块数据（网事杂谈的名字随 board_list.json 变化）。
 */
enum class MainTab(val label: String, val icon: ImageVector, val title: String?) {
    BOOKMARK("我的收藏", Icons.Default.Star, "我的收藏"),
    FORUM("网事杂谈", Icons.Default.Home, null),
    MINE("我的", Icons.Default.Person, "我的"),
}
```

- [ ] **Step 3: 新建底部导航栏 Composable**

在同一个 `MainTab.kt` 文件末尾追加（与 Tab 定义同文件，二者一起变化）：

```kotlin
@Composable
fun MainBottomBar(current: MainTab, onSelect: (MainTab) -> Unit) {
    NavigationBar {
        MainTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == current,
                onClick = { onSelect(tab) },
                icon = { Icon(imageVector = tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) }
            )
        }
    }
}
```

并在文件 import 区补上：

```kotlin
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
```

（`NavigationBar` 只有 material3 有，material 1.x 没有；这里用 material3，与项目已有的
`ModalNavigationDrawer` / `HorizontalDivider` 用法一致。）

- [ ] **Step 4: 改造 `NavigationDrawerFragment.NavigationDrawerView`**

把 `ScaffoldApp` 那一段（当前第 353-359 行）替换为下面内容。选中项用 `rememberSaveable` 存，
旋转屏幕不丢：

```kotlin
            var currentTab by rememberSaveable { mutableStateOf(MainTab.BOOKMARK) }
            val title = currentTab.title
                ?: forumBoardViewModel.forumBoard?.name
                ?: activity?.title.toString()

            ScaffoldApp(
                topAppBarData = getTopAppBarData(
                    title = title,
                    navigationIconAction = {
                        scope.launch {
                            drawerState.open()
                        }
                    }),
                bottomBar = {
                    MainBottomBar(current = currentTab) { currentTab = it }
                }
            ) {
                when (currentTab) {
                    MainTab.BOOKMARK -> BookmarkTabView(forumBoardViewModel, stockViewModel)
                    MainTab.FORUM -> ForumTabContent(forumBoardViewModel)
                    MainTab.MINE -> MineView(viewModel, requireActivity())
                }
            }
```

- [ ] **Step 5: 顶栏只留搜索，标题可传入**

同文件中，把 `getTopAppBarData` 与 `getOptionMenuData` 两个方法整体替换为：

```kotlin
    private fun getTopAppBarData(
        title: String,
        navigationIconAction: (() -> Unit)? = null
    ): TopAppBarData {
        val topAppBarData = TopAppBarData(title = title)
        topAppBarData.navigationIconAction = navigationIconAction
        topAppBarData.optionMenuData = getOptionMenuData()
        return topAppBarData
    }

    /** 其余入口已迁到「我的」页；搜索是全局动作，留在顶栏 */
    private fun getOptionMenuData(): List<OptionMenuData> {
        return arrayListOf(
            OptionMenuData(title = "搜索用户", action = {
                viewModel.startSearchActivity(requireActivity())
            }, type = OptionMenuData.OPTION_MENU_TYPE_ALWAYS_SHOW, icon = R.drawable.btn_ic_search),
        )
    }
```

注意 `OptionActionMenu` 无论 `hideItems` 是否为空都会画出三点按钮。为了让三点菜单彻底消失，
在 `ScaffoldApp.kt` 的 `OptionActionMenu` 中给三点按钮加空判断——把 `var expanded` 那段包起来：

```kotlin
        if (hideItems.isEmpty()) {
            return@Row
        }
        var expanded by remember { mutableStateOf(false) }
```

放在 `showItems.forEach { ... }` 之后、`var expanded` 之前。

- [ ] **Step 6: 新增 `ForumTabContent`**

`ForumBoardContent` 是按下标取板块的，底部导航需要按语义取。在 `MainTab.kt` 末尾追加：

```kotlin
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
    } else {
        ForumBoardGridContent(forumBoard, forumBoardViewModel)
    }
}
```

import 区补上：

```kotlin
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import gov.anzong.androidnga.activity.compose.board.ForumBoardGridContent
import gov.anzong.androidnga.activity.compose.board.ForumBoardViewModel
import gov.anzong.androidnga.core.board.data.BoardEntity
```

- [ ] **Step 7: 从 `ForumBoardContent` 抽出 `ForumBoardGridContent`**

在 `ForumBoardView.kt` 中，把 `ForumBoardContent` 的 else 分支抽成一个接收 `BoardEntity` 的公开函数，
供 Step 6 调用。整体替换 `ForumBoardContent`：

```kotlin
@Composable
fun ForumBoardContent(
    index: Int,
    forumBoardViewModel: ForumBoardViewModel
) {
    val boardData = forumBoardViewModel.getBoardData(index)
    if (boardData.type == BoardEntity.BoardType.BOOKMARK) {
        ForumBoardBookmarkContent(boardData, forumBoardViewModel)
    } else {
        ForumBoardGridContent(boardData, forumBoardViewModel)
    }
}

/** 版面九宫格，含 GROUP 分组标题 */
@Composable
fun ForumBoardGridContent(
    boardData: BoardEntity,
    forumBoardViewModel: ForumBoardViewModel
) {
    val boardList: List<BoardEntity> = boardData.children ?: emptyList()
    val maxColumn = 3
    LazyVerticalGrid(
        columns = GridCells.Fixed(maxColumn),
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 8.dp, end = 8.dp)
    ) {
        boardList.forEach {
            if (it.type == BoardEntity.BoardType.GROUP) {
                item(span = { GridItemSpan(maxColumn) }) {
                    ForumBoardGroupView(it)
                }
                it.children?.let { data ->
                    items(data.size) { index ->
                        ForumBoardGridItemView(data[index], forumBoardViewModel)
                    }
                }
            } else {
                item {
                    ForumBoardGridItemView(it, forumBoardViewModel)
                }
            }
        }
        item(span = { GridItemSpan(maxColumn) }) {
            val paddingValues = WindowInsets.navigationBars.asPaddingValues()
            Spacer(modifier = Modifier.height(paddingValues.calculateBottomPadding()))
        }
    }
}
```

注意 `ForumBoardGridItemView` 当前是 `private`，`ForumBoardGridContent` 与它同文件，无需改可见性。

- [ ] **Step 8: 清理 `NavigationDrawerFragment` 的 import**

删除 `import gov.anzong.androidnga.activity.compose.board.ForumBoardView`（不再调用），
补上：

```kotlin
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import gov.anzong.androidnga.activity.compose.main.BookmarkTabView
import gov.anzong.androidnga.activity.compose.main.ForumTabContent
import gov.anzong.androidnga.activity.compose.main.MainBottomBar
import gov.anzong.androidnga.activity.compose.main.MainTab
import gov.anzong.androidnga.activity.compose.main.MineView
```

`ForumBoardView`（顶层带 pager 的那个 Composable）此时已无调用方。保留它不会报错，
但为避免死代码，从 `ForumBoardView.kt` 中删除该函数及其专用 import
（`TabLayoutWithPager`）。若删除后 `ForumBoardContent(index, ...)` 也无调用方，
一并删除 `ForumBoardContent`——用 grep 确认后再删：

Run: `grep -rn "ForumBoardContent\|ForumBoardView(" nga_phone_base_3.0/src/main/java/`

- [ ] **Step 9: 编译验证**

Run: `./gradlew :nga_phone_base_3.0:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: 人工走查**

安装 APK，逐项确认：
- 底部出现三个 Tab，可切换，标题跟随变化（收藏页「我的收藏」、版面页「网事杂谈」、我的页「我的」）
- 我的收藏 → 版面 / 自选股 二级 tab 均可切换；自选股的添加、长按删除、设置建仓价都正常
- 网事杂谈 → 九宫格分组标题正常，点版面进帖子列表
- 我的 → 6 个入口全部能跳转；头像区已登录进个人主页、未登录进登录页
- 左上角抽屉仍能拉出，6 个条目功能不变
- 右上角只剩放大镜，无三点菜单
- 空收藏时版面页显示引导文案，不崩溃
- 未登录时「我的」页显示「未登录」，不崩溃
- 旋转屏幕后停留在原 Tab

- [ ] **Step 11: Commit**

```bash
git add -A
git commit -m "feat: 首页改为底部三 Tab 导航，溢出菜单迁入「我的」"
```

---

## Self-Review 记录

- **Spec coverage**：底部三 Tab（Task 4）、溢出菜单迁移（Task 3 + Task 4 Step 5）、
  自选股并入收藏（Task 1 + Task 2）、抽屉保留（全程不动）、空态处理（Task 2 Step 1、Task 4 Step 6）——
  spec 各节均有对应任务。
- **Type consistency**：`ForumBoardGridContent(boardData, forumBoardViewModel)` 在 Task 4 Step 6
  与 Step 7 签名一致；`MineView(viewModel, activity)` 在 Task 3 定义、Task 4 Step 4 调用一致；
  `ScaffoldApp` 的 `bottomBar` 参数在 Step 1 定义、Step 4 使用一致。
- **已知中间态**：Task 1 结束时自选股暂时无入口（Task 2 恢复），已在该任务走查项中说明。
