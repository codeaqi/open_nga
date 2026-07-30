# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Open-source Android client for the NGA forum (an+zong/justwen "androidnga"). Multi-module Gradle project targeting Android, mixed Java/Kotlin with Jetpack Compose for newer UI. Published to F-Droid and Google Play as `gov.anzong.androidnga`.

## Build

Standard Android Studio / Gradle project — open the root directory directly in Android Studio, or use the wrapper:

```bash
./gradlew assembleDebug
```

```bash
./gradlew assembleRelease
```

To build/test a single module, use its Gradle path from [settings.gradle](settings.gradle), e.g.:

```bash
./gradlew :lib_core:testDebugUnitTest
```

```bash
./gradlew :nga_phone_base_3.0:assembleDebug
```

Release builds sign with a keystore expected at `..\keystore\android19910914.keystore` (relative to the app module) unless the `IS_JENKINS` project property is set, in which case it looks for `../../android19910914.keystore`. This keystore is not in the repo; debug builds (`applicationIdSuffix '.debug'`, unsigned/no minify) are the normal local workflow.

- Kotlin 2.0.21, JVM target 17, compileSdk/targetSdk 35, minSdk 30.
- `ndk.abiFilters` is restricted to `arm64-v8a` in the app module.
- Dependency versions are split between the old-style `ext {}` block in the root [build.gradle](build.gradle) (e.g. `compose_version`, `retrofit_version`, `arouter_version`) and the newer [gradle/libs.versions.toml](gradle/libs.versions.toml) catalog (currently just Kotlin/fastjson2/AGP) — check both when bumping a dependency.
- Repositories are Aliyun mirrors + jitpack + Google/Maven Central (see root `build.gradle`); there's no corporate/internal repo.

## Module architecture

This is an ARouter-based, multi-module app. The dependency direction is: **base/foundation modules → API/interface module → business modules → app module**. Modules never depend directly on each other's implementations across business boundaries — they talk through interfaces declared in `lib_base_service_api` and looked up via ARouter's `@Route` path constants at runtime.

```
nga_phone_base_3.0/         the app module (applicationId gov.anzong.androidnga) — Application class,
                            activities, ARouter service implementations, glue code. Depends on
                            every library module.

lib_base_service_api/       cross-module contracts only: IProvider interfaces (IUserManagerService,
                            IThemeManagerService) + ARouter ROUTER_PATH constants + ARouterConstants.
                            Business/app modules implement these; other modules call through them via
                            ARouter.getInstance().navigation(...) instead of a compile dependency.

lib_base_common/            lowest-level shared utilities, base Activity/Fragment classes, KV storage,
                            common widgets/dialogs. Almost everything depends on this.
lib_base_logger/            logging facade (depends on lib_base_common).
lib_base_network/           Retrofit/OkHttp setup (RetrofitHelper, converters) (depends on
                            lib_base_common, lib_base_logger).
lib_base_ui/                shared non-Compose UI (e.g. FragmentTemplateActivity) (depends on
                            lib_base_service_api).
lib_base_ui_compose/        shared Compose theme/widgets (depends on lib_base_service_api,
                            lib_base_common).
lib_core/                   forum content domain logic: BBCode/HTML post decoding and building
                            (ForumDecoder family, HtmlBuilder family), dice-roll logic. Kept
                            dependency-light (compileOnly lib_base_common) since it's core parsing logic.
lib_core_data/               core data layer (depends on lib_base_common).

lib_bu_account/             account/user business module: login, user switching/multi-account
                            (UserManager, AppDatabase user table), profile screens (depends on
                            lib_base_common, lib_base_service_api, lib_base_ui_compose).
lib_bu_message/              private messages feature (list/detail/post) (depends on lib_base_common,
                            lib_base_network, lib_base_ui_compose, lib_base_service_api, lib_core_data).
lib_bu_statistics/           analytics/crash reporting integration (Bugly, Umeng — see
                            com.justwen.androidnga.cloud.*) (depends on lib_base_common).
lib_module_debug/            in-app debug tools/log viewer (DebugActivity, DebugManager, FileLogger)
                            (depends on lib_base_ui_compose, lib_base_service_api, lib_base_common,
                            lib_base_logger).
```

Note the package-name split: base/core modules and the app module mostly use `gov.anzong.androidnga`, while most library modules use `com.justwen.androidnga` / `com.justwent.androidnga` — this is historical, not a meaningful architectural boundary. Don't assume package name implies module ownership; check `build.gradle`'s `namespace` and the module's actual directory.

### Cross-module service pattern

To add a capability that one module exposes to others without a hard dependency:
1. Declare an `IProvider` subinterface + `ROUTER_PATH` const in `lib_base_service_api` (see `IUserManagerService.kt`, `IThemeManagerService.kt`).
2. Implement it wherever makes sense (commonly the app module or the owning business module), annotated `@Route(path = ...)` (see `UserManagerService.kt`, `ThemeManagerService.kt`).
3. Consumers fetch it via ARouter navigation rather than importing the impl module directly.

### App module structure (`nga_phone_base_3.0`)

- `gov.anzong.androidnga` — `NgaClientApp` (Application entry point), activities (`activity/`, with newer screens under `activity/compose/`), ARouter service impls (`service/`), ARouter path constants/interceptor (`arouter/`).
  - `activity/compose/stock/` — a watchlist feature unrelated to the forum: quotes come from Sina's public endpoint over a dedicated OkHttp client, and per-stock target prices are kept in preferences. A useful template for "new Compose screen with its own data source".
- `sp.phone` — older MVP-style code (contract/model/presenter/viewmodel under `mvp/`), HTTP beans, adapters, custom views. Legacy but still active; new work generally goes in `gov.anzong.androidnga` and favors Compose + ViewModel over the `sp.phone.mvp` contract/presenter pattern.

`NgaClientApp.onCreate()` is the best map of app startup order: logger init → preference migration → version-upgrade check → `AppDatabase.init` → core module init (user manager, cookie provider wiring) → ARouter init → auto check-in → crash/cloud reporting init.

### Forum content pipeline (`lib_core`)

Posts are parsed/built through a decoder/builder chain rather than a single parser:
- Decoding (HTML/BBCode → structured data): `IForumDecoder` implementations — `ForumBasicDecoder`, `ForumImageDecoder`, `ForumEmoticonDecoder`, `ForumAlbumDecoder`, `ForumVoteDecoder`, `ForumDiceDecoder` (dice-roll parsing, Kotlin).
- Building (structured data → HTML for posting): `IHtmlBuild` implementations — `HtmlBuilder`, `HtmlAttachmentBuilder`, `HtmlCommentBuilder`, `HtmlSignatureBuilder`, `HtmlTailBuilder`, `HtmlVoteBuilder`.

When adding support for a new BBCode/content type, add a decoder and/or builder to this chain rather than special-casing it elsewhere.

### Key third-party pieces

- **ARouter** — inter-module navigation and service discovery (see above).
- **Retrofit + RxJava2** — networking (`lib_base_network`), cookie provider wired from `UserManagerImpl` in `NgaClientApp`.
- **Room** — local persistence (`AppDatabase`, e.g. multi-user table in `lib_bu_account`).
- **fastjson2** — JSON (recently migrated to from fastjson1; both may still appear depending on module).
- **Glide, ButterKnife, Compose, Material** — UI layer, mixed old (ButterKnife/XML) and new (Compose) depending on module age.
- **Bugly / Umeng** — crash reporting and analytics, gated through `lib_bu_statistics`.

## Gotchas

Traps that cost real debugging time. Most are not visible from reading the happy path.

### Build

- **Missing `kotlinOptions { jvmTarget = '17' }`** — several library modules set `compileOptions` to Java 17 but omit the Kotlin counterpart, so Kotlin defaults to the JDK running Gradle (21 on a modern Android Studio) and the build dies with *"Inconsistent JVM-target compatibility"*. Already fixed in `lib_base_service_api`, `lib_base_common`, `lib_base_network`. If a **new** module gets Kotlin sources, add `kotlinOptions` alongside `compileOptions`. Modules without the `kotlin-android` plugin (`lib_base_logger`, `lib_bu_statistics`, `lib_core_data`) don't need it.
- **OkHttp in Kotlin** — `response.code()` / `body()` are deprecated Java-style getters; Kotlin code must use the properties `response.code` / `response.body`. Existing OkHttp callers are Java, so this only bites in new Kotlin files.
- `combinedClickable` needs `@OptIn(ExperimentalFoundationApi::class)` at this Compose version (see `FilterWordFragment.kt` for the established pattern).

### Preferences: two different stores

There are **two** SharedPreferences files and mixing them silently breaks state sync:

- `PhoneConfiguration` reads/writes the **named** file `PreferenceKey.PERFERENCE` (note the typo in the constant) and registers a change listener on it to keep its in-memory fields fresh.
- `PreferenceUtils` uses the **default** SharedPreferences.

To make a settings toggle take effect immediately, write to the same file `PhoneConfiguration` listens on — otherwise the listener never fires and the cached field stays stale.

### Board list (`board_list.json`)

- The home-screen tabs are `[bookmarkBoard] + localBoardList`, so **top-level order in [board_list.json](nga_phone_base_3.0/src/main/assets/board_list.json) is the tab order**, with "我的收藏" always pinned first.
- The asset is only read on first launch; afterwards `ForumBoardModel` loads the copy cached in `filesDir`. **Editing the asset alone changes nothing on an existing install.** Bump `BOARD_LOCAL_VERSION_CURRENT` in [ForumBoardRepository.kt](nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/compose/board/ForumBoardRepository.kt) — `checkLocalDataVersion` then deletes the cached file and re-reads the asset. This is far better than telling users to clear app data, which also wipes cached posts, login, and settings.
- `ForumBoardModel.loadIncrementalBoardList()` hardcodes category ids (`other`, `company`) when merging newly-published boards from the server. Deleting a top-level board from the asset means removing its id here too, or you leave a dangling reference.
- The file contains a **pre-existing trailing comma** (`"type": 0,` before `}`). fastjson tolerates it; `JSON.parse` does not. Don't "fix" it reflexively and don't let a strict validator convince you the file is broken.
- Bookmarked boards are stored separately by fid, so deleting a category from the asset does not remove it from a user's 我的收藏.

### Storage & permissions (minSdk 30)

`WRITE_EXTERNAL_STORAGE` is dead on Android 11+ — a request always resolves false. Any code gated on it is unreachable. Cache export/import were both broken this way and now use SAF (`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`), which needs no permission at all. Use SAF for anything touching user-visible storage. Also don't trust the MIME type from a document picker (`application/octet-stream` is common for zips) — fall back to the filename extension.

### Legacy MVP layer (`sp.phone.mvp`)

- **`ArticleListModel.loadPage(param, callBack)`** (the 2-arg overload) passes `null` as the Retrofit `@HeaderMap`, which throws — `@HeaderMap` rejects null. Nothing in the original code called it, so the bug lay dormant. Always use the 3-arg overload with a non-null (possibly empty) map.
- `BaseModel.getLifecycleProvider()` is null until a `BasePresenter` binds a View. Instantiating a Model directly from a background task (no Fragment) means `loadPage`'s `bindUntilEvent` composes against null. Set your own provider — an unfired `BehaviorSubject` never emits, so it won't cut the stream.
- "缓存本页" writes data already held in memory by the visible `ArticleListFragment`; it does **not** fetch. Anything that caches pages the user hasn't visited has to make its own requests (see `TopicCacheAllTask`).
- Post pages are 20 replies each; total pages is `ceil(__ROWS / 20.0)`. Cache layout is `filesDir/cache/<tid>/` with `<tid>.json` (the `ThreadPageInfo` descriptor) plus one `<page>.json` per page.
- `TopicListAdapter` is shared by the board list, search results, and the cache list. Anything cache-specific must be behind a flag (e.g. `setShowNewReplyTag`) or it leaks into the other screens.

### NGA API notes

- Thread lists sort by last-reply time by default; append `&order_by=postdatedesc` for post-time order. The 精华区 request already does this.
- `ServerException` ("NGA后台抽风了") means the response had no parseable `data` — often rate limiting. `ArticleListPresenter` retries with the next account's cookie, which is why multi-account helps.
- Cookies are injected globally by an OkHttp interceptor in `RetrofitHelper`, not per-request. Third-party endpoints (e.g. the stock quotes in `activity/compose/stock`) deliberately use their own OkHttp client so NGA cookies and UA don't leak.
