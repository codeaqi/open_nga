# 首页底部导航重构设计

日期：2026-07-29

## 背景

当前首页（`NavigationDrawerFragment`）结构是：抽屉 + 顶部 AppBar + 一个横向 pager。pager 的 tab 由
`ForumBoardViewModel.boardLiveData` 驱动，内容为 `[我的收藏] + [自选股] + board_list.json 的顶级板块`。
由于 `board_list.json` 目前只剩一个顶级板块「网事杂谈」，实际呈现就是三个横向 tab：
我的收藏 / 自选股 / 网事杂谈。

问题：
- 三个功能完全异构的页面（收藏夹、股票行情、论坛板块）被塞进同一个横向 pager，左右滑动语义混乱。
- 常用入口（我的主题、我的回复、短消息、设置等）藏在右上角三个点的溢出菜单里，需要两次点击且不可发现。
- 自选股作为一个伪版面 (`BoardType.STOCK`) 插进 `boardList` 的下标 1，是个 hack：它不参与
  `boardMap`，不参与本地持久化，却要求所有按下标访问 `boardList` 的代码都记得跳过它。

## 目标

1. 首页底部增加三个 Tab：我的收藏 / 网事杂谈 / 我的。
2. 右上角溢出菜单的功能迁移到「我的」页。
3. 自选股并入「我的收藏」，不再是顶层 Tab。

## 非目标

- 不改抽屉。抽屉（登录账号、添加版面ID、由URL读取、清空我的收藏、最近被喷、关于）保持原样，
  仍从左上角汉堡菜单拉出。
- 不改 `StockView` / `StockViewModel` 的任何内部逻辑，只改它的挂载位置。
- 不改板块数据源、`board_list.json`、`BOARD_LOCAL_VERSION_CURRENT`。板块内容本身不动。
- 不做底部 Tab 的徽标/红点。「最近被喷」的未读数仍只在抽屉里显示。

## 设计

### 页面结构

```
ScaffoldApp
├─ TopAppBar        标题随当前 Tab 变化；仅保留「搜索用户」图标，溢出菜单去掉
├─ content          当前选中 Tab 的页面
└─ NavigationBar    我的收藏 | 网事杂谈 | 我的
```

三个 Tab：

| Tab | 标题 | 内容 |
|---|---|---|
| 我的收藏 | 我的收藏 | 二级 TabRow：版面 \| 自选股 |
| 网事杂谈 | 取自板块数据的第一个顶级板块名 | 现有的版面九宫格 |
| 我的 | 我的 | 功能入口列表 |

Tab 切换用 `rememberSaveable` 保存选中项，屏幕旋转后不丢。三个页面各自独立组合，
切换时重建（不做 pager，不做左右滑动）——它们内容异构，横滑没有意义。

### 我的收藏页

`BookmarkTabView`：复用现成的 `TabLayoutWithPager(tabs = ["版面", "自选股"], fixed = true)`。

- 「版面」页 = 现有 `ForumBoardBookmarkContent`，原样搬过来。
- 「自选股」页 = 现有 `StockView(stockViewModel)`，原样搬过来。

选「版面」为默认页。原先「收藏为空时跳到第一个真实版面」的逻辑作废——底部 Tab 让用户随时能到
网事杂谈，不需要靠初始页去补偿空收藏。收藏为空时「版面」页显示一句引导文案。

### 网事杂谈页

现有 `ForumBoardContent` 的非 bookmark 分支，即板块九宫格（含 GROUP 分组标题）。
数据取 `boardList` 中第一个 `type != BOOKMARK` 的板块。

`board_list.json` 未来若恢复多个顶级板块，这个 Tab 只显示第一个。这是当前数据形态下的有意简化；
若日后板块变多，此处需要改回一个横向 pager（见「已知限制」）。

### 我的页

一个 `LazyColumn` 列表，项即现有溢出菜单的 6 项，全部复用
`NavigationDrawerViewModel` 已有的跳转方法，不新写导航逻辑：

| 条目 | 调用 |
|---|---|
| 我的主题 | `startPostPage(context, false)` |
| 我的回复 | `startPostPage(context, true)` |
| 我的缓存 | `startCacheTopicPage(context)` |
| 短消息 | `startMessagePage(context)` |
| 收藏夹 | `startFavoriteTopicPage(context)` |
| 设置 | `startSettingsPage(activity)` |

顶部放一个用户信息条（头像 + 昵称/未登录），点击行为与抽屉头部一致：已登录进个人主页，
未登录进登录页。复用 `viewModel.startProfilePage` / `startLoginPage`。

「搜索用户」不进「我的」——它是个全局动作，留在顶栏图标位置。

### 数据层改动

移除自选股伪版面：

- `ForumBoardModel`：删掉 `createStockBoard()`、`STOCK_BOARD_ID`，以及 `boardList.add(1, ...)`。
  `boardList` 恢复为 `[bookmarkBoard] + localBoardList`。
- `BoardEntity.BoardType.STOCK` 常量删除（无其它引用）。
- `ForumBoardViewModel` 增加两个按语义取数据的属性，替代按下标取：
  - `bookmarkBoard: BoardEntity` —— `type == BOOKMARK` 的那个
  - `forumBoard: BoardEntity?` —— 第一个 `type != BOOKMARK` 的
  保留 `getBoardData(index)` 不动（其它调用方仍在用）。

这样底部导航不依赖 `boardList` 的下标顺序，去掉 stock 占位后不会错位。

### 文件落点

新增：
- `activity/compose/main/MainTab.kt` —— Tab 枚举（id / 标题 / 图标）
- `activity/compose/main/MainBottomNavView.kt` —— `NavigationBar` + 内容分发
- `activity/compose/mine/MineView.kt` —— 我的页

修改：
- `activity/compose/drawer/NavigationDrawerFragment.kt` —— `ScaffoldApp` 内容换成底部导航壳；
  `getOptionMenuData()` 只留搜索用户；标题改为跟随 Tab
- `activity/compose/board/ForumBoardView.kt` —— 拆出 `ForumBoardBookmarkContent` 与
  九宫格供底部导航直接调用；删掉顶层 `ForumBoardView` 的 pager 与 STOCK 分支
- `activity/compose/board/ForumBoardModel.kt` —— 删除 stock 伪版面
- `activity/compose/board/ForumBoardViewModel.kt` —— 增加 `bookmarkBoard` / `forumBoard`
- `core/board/data/BoardEntity.kt` —— 删除 `BoardType.STOCK`

图标：底部 Tab 用 `androidx.compose.material.icons` 内置矢量图（Star / Forum / Person），
不新增 drawable 资源。

### 状态与生命周期

`ForumBoardViewModel` 是 `object` 单例，`StockViewModel` 是普通 ViewModel，
两者的获取方式不变，仍由 `NavigationDrawerFragment` 持有并向下传递。
底部 Tab 切换不销毁 ViewModel，自选股行情的轮询状态跨 Tab 保持。

### 错误处理

- `forumBoard` 为 null（板块数据为空或仍在加载）时，网事杂谈 Tab 显示加载/空态文案，不崩溃。
  现有代码 `boardData?.let { }` 已有这层保护，保持。
- `bookmarkBoard.children` 为空时，版面页显示引导文案而非空白网格。

## 测试

本项目无 Compose UI 测试基建，且改动集中在 UI 组装层（无新增可纯函数化的逻辑）。
验证方式为编译 + 手动走查：

1. `./gradlew :nga_phone_base_3.0:assembleDebug` 通过。
2. 手动检查清单：
   - 底部三个 Tab 可切换，标题跟随变化
   - 我的收藏 → 版面/自选股 二级 tab 均正常，自选股增删改仍可用
   - 网事杂谈 → 九宫格分组显示正常，点击进帖子列表
   - 我的 → 6 个入口全部可跳转
   - 左上角抽屉仍可拉出，各项功能不变
   - 右上角只剩搜索图标，无三点菜单
   - 空收藏 / 未登录状态不崩溃

## 已知限制

- 网事杂谈 Tab 只呈现第一个顶级板块。这与当前 `board_list.json` 的形态一致
  （只有一个顶级板块）。若之后板块恢复多个，需在该 Tab 内引入横向 pager。
- 底部 Tab 不带未读徽标。
