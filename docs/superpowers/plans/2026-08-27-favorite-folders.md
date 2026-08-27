# 收藏夹搜索与文件夹分类 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给 NGA 收藏夹加本地搜索，并支持把收藏的帖子归类到自建文件夹。

**Architecture:** NGA 收藏夹是服务端分页列表且接口没有文件夹概念，所以维护一份本地快照 JSON，搜索和文件夹都建立在它之上。纯逻辑放在无 Android 依赖的 `FavoriteSnapshot` 里以便单测，IO 与单例放在 `FavoriteStore`，UI 改造集中在 `TopicFavoriteFragment`。

**Tech Stack:** Java 17、fastjson（`com.alibaba.fastjson`）、commons-io、JUnit 4、ButterKnife、RecyclerView（既有 MVP 层）

**Spec:** `docs/superpowers/specs/2026-08-27-favorite-folders-design.md`

## Global Constraints

- 新代码用 **Java**，包名 `gov.anzong.androidnga.favorite`。消费方 `TopicFavoriteFragment` 是 Java，用 Java 可避免 Kotlin `object` 的 `INSTANCE.` 互操作噪音。
- 单测放 `nga_phone_base_3.0/src/test/java/`，JUnit 4，与既有 `sp/phone/task/CacheUpdateQueueTest.java` 同风格。
- 编译/测试前必须设 `JAVA_HOME`：`export JAVA_HOME="D:/Program Files/Android/Android Studio/jbr"`（Git Bash 里没有 java）。
- **`folder` 空串表示未分类**，全程不用 null 表示未分类，避免两种「空」并存。
- **upsert 绝不覆盖已有 `folder`**，日常加载**绝不删条目**。这两条是数据安全底线，由 Task 1 的测试锁死。
- 装机验证：adb 全路径 `C:/Users/codea/AppData/Local/Android/Sdk/platform-tools/adb.exe`，设备 `374d7eee`，包名 `gov.anzong.androidnga.debug`。`adb shell` 里出现 `/sdcard/...` 时命令前加 `MSYS_NO_PATHCONV=1`，否则 Git Bash 会把它改写成 Windows 路径。

---

### Task 1: FavoriteItem 与 upsert 规则

**Files:**
- Create: `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/favorite/FavoriteItem.java`
- Create: `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/favorite/FavoriteSnapshot.java`
- Test: `nga_phone_base_3.0/src/test/java/gov/anzong/androidnga/favorite/FavoriteSnapshotUpsertTest.java`

**Interfaces:**
- Consumes: 无
- Produces: `FavoriteItem`（公有字段 `tid/subject/author/fid/board/replies/postDate/folder/updateTime`）；`FavoriteSnapshot` 的公有字段 `List<String> folders`、`Map<String, FavoriteItem> items`，方法 `void upsert(FavoriteItem)`、`void setFolder(int, String)`、`String folderOf(int)`

- [ ] **Step 1: 写失败的测试**

```java
package gov.anzong.androidnga.favorite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class FavoriteSnapshotUpsertTest {

    private static FavoriteItem item(int tid, String subject, int replies) {
        FavoriteItem i = new FavoriteItem();
        i.tid = tid;
        i.subject = subject;
        i.author = "张三";
        i.fid = 706;
        i.board = "大时代";
        i.replies = replies;
        i.postDate = 1690000000;
        return i;
    }

    /**
     * 核心回归用例：服务端列表刷新时绝不能把用户设好的文件夹冲掉。
     * 写错这里，用户每下拉刷新一次，全部分类就没了。
     */
    @Test
    public void upsertKeepsExistingFolder() {
        FavoriteSnapshot snapshot = new FavoriteSnapshot();
        snapshot.upsert(item(1, "老标题", 10));
        snapshot.setFolder(1, "红利");

        snapshot.upsert(item(1, "新标题", 25));

        assertEquals("folder 必须保留", "红利", snapshot.folderOf(1));
        assertEquals("标题要刷新", "新标题", snapshot.items.get("1").subject);
        assertEquals("回复数要刷新", 25, snapshot.items.get("1").replies);
    }

    /** 新条目默认未分类，用空串而不是 null */
    @Test
    public void newItemIsUnfiled() {
        FavoriteSnapshot snapshot = new FavoriteSnapshot();
        snapshot.upsert(item(2, "标题", 3));
        assertEquals("", snapshot.folderOf(2));
    }

    /** 日常增量加载只加不删：只加载了第 1 页时，第 2 页的收藏不能因为没出现就被清掉 */
    @Test
    public void upsertNeverRemovesOtherItems() {
        FavoriteSnapshot snapshot = new FavoriteSnapshot();
        snapshot.upsert(item(1, "第一页的", 1));
        snapshot.upsert(item(2, "第二页的", 1));

        snapshot.upsert(item(1, "第一页的-刷新", 2));

        assertEquals(2, snapshot.items.size());
    }

    /** 没见过的 tid 查文件夹返回空串，不抛异常 */
    @Test
    public void folderOfUnknownTidIsEmpty() {
        assertEquals("", new FavoriteSnapshot().folderOf(999));
    }

    /** updateTime 每次 upsert 都刷新 */
    @Test
    public void upsertRefreshesUpdateTime() {
        FavoriteSnapshot snapshot = new FavoriteSnapshot();
        FavoriteItem first = item(3, "标题", 1);
        first.updateTime = 100L;
        snapshot.upsert(first);

        FavoriteItem second = item(3, "标题", 1);
        second.updateTime = 200L;
        snapshot.upsert(second);

        assertEquals(200L, snapshot.items.get("3").updateTime);
    }

    /** items 用 tid 字符串做键 */
    @Test
    public void itemsAreKeyedByTidString() {
        FavoriteSnapshot snapshot = new FavoriteSnapshot();
        snapshot.upsert(item(42, "标题", 1));
        assertNull(snapshot.items.get("43"));
        assertEquals(42, snapshot.items.get("42").tid);
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
export JAVA_HOME="D:/Program Files/Android/Android Studio/jbr" && ./gradlew.bat :nga_phone_base_3.0:testDebugUnitTest --tests "gov.anzong.androidnga.favorite.FavoriteSnapshotUpsertTest" --console=plain
```

Expected: 编译失败，`cannot find symbol: class FavoriteItem`

- [ ] **Step 3: 写实现**

`FavoriteItem.java`：

```java
package gov.anzong.androidnga.favorite;

/**
 * 本地收藏快照里的一条帖子。
 *
 * 字段公有是为了让 fastjson 直接序列化，不写一堆 getter/setter。
 */
public class FavoriteItem {

    public int tid;

    public String subject = "";

    public String author = "";

    public int fid;

    /** 版面名，列表里显示用 */
    public String board = "";

    public int replies;

    public int postDate;

    /** 所属文件夹，**空串表示未分类**。全程不用 null，避免两种「空」并存 */
    public String folder = "";

    /** 最后一次从服务端刷新到的时间 */
    public long updateTime;
}
```

`FavoriteSnapshot.java`（本任务只实现三个方法，其余后续任务补）：

```java
package gov.anzong.androidnga.favorite;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地收藏快照的内存模型与全部业务逻辑。
 *
 * 刻意不依赖任何 Android API，好让规则能直接单测——这里最容易写错的是
 * 「刷新时把用户的分类冲掉」和「增量加载时误删没加载到的条目」，
 * 两者都会静默毁掉用户数据，必须由测试锁死。
 *
 * 磁盘读写在 FavoriteStore，本类只管内存里的状态。
 */
public class FavoriteSnapshot {

    /** 文件夹名，有序；允许空文件夹（可以先建夹再往里放帖子） */
    public List<String> folders = new ArrayList<>();

    /** tid 字符串 -> 条目。用 Map 让 upsert 是 O(1) */
    public Map<String, FavoriteItem> items = new LinkedHashMap<>();

    /**
     * 写入一条从服务端列表拿到的收藏。
     *
     * 已存在的条目**只刷新服务端字段，绝不动 folder**；不存在则新增为未分类。
     * 本方法永远不删除任何条目——调用方通常只加载了收藏夹的前几页，
     * 按当前页结果做清理会把没加载到的收藏全删光。
     */
    public void upsert(FavoriteItem incoming) {
        String key = String.valueOf(incoming.tid);
        FavoriteItem existing = items.get(key);
        if (existing == null) {
            if (incoming.folder == null) {
                incoming.folder = "";
            }
            items.put(key, incoming);
            return;
        }
        existing.subject = incoming.subject;
        existing.author = incoming.author;
        existing.fid = incoming.fid;
        existing.board = incoming.board;
        existing.replies = incoming.replies;
        existing.postDate = incoming.postDate;
        existing.updateTime = incoming.updateTime;
    }

    /** 设置某个帖子的文件夹，空串表示移出文件夹 */
    public void setFolder(int tid, String folder) {
        FavoriteItem item = items.get(String.valueOf(tid));
        if (item != null) {
            item.folder = folder == null ? "" : folder;
        }
    }

    /** 没收录过的 tid 返回空串（视为未分类），不抛异常 */
    public String folderOf(int tid) {
        FavoriteItem item = items.get(String.valueOf(tid));
        return item == null ? "" : item.folder;
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

```bash
export JAVA_HOME="D:/Program Files/Android/Android Studio/jbr" && ./gradlew.bat :nga_phone_base_3.0:testDebugUnitTest --tests "gov.anzong.androidnga.favorite.FavoriteSnapshotUpsertTest" --console=plain
```

Expected: 6 个测试全部 PASS

- [ ] **Step 5: 提交**

```bash
git add nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/favorite/ nga_phone_base_3.0/src/test/java/gov/anzong/androidnga/favorite/
git commit -m "feat: 收藏快照的条目模型与 upsert 规则"
```

---

### Task 2: 文件夹的增删改

**Files:**
- Modify: `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/favorite/FavoriteSnapshot.java`
- Test: `nga_phone_base_3.0/src/test/java/gov/anzong/androidnga/favorite/FavoriteSnapshotFolderTest.java`

**Interfaces:**
- Consumes: Task 1 的 `upsert`、`setFolder`、`folderOf`、`items`、`folders`
- Produces: `boolean createFolder(String)`、`boolean renameFolder(String oldName, String newName)`、`void deleteFolder(String)`

- [ ] **Step 1: 写失败的测试**

```java
package gov.anzong.androidnga.favorite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class FavoriteSnapshotFolderTest {

    private static FavoriteItem item(int tid) {
        FavoriteItem i = new FavoriteItem();
        i.tid = tid;
        i.subject = "标题" + tid;
        return i;
    }

    @Test
    public void createFolderAppendsInOrder() {
        FavoriteSnapshot s = new FavoriteSnapshot();
        assertTrue(s.createFolder("红利"));
        assertTrue(s.createFolder("银行"));
        assertEquals(Arrays.asList("红利", "银行"), s.folders);
    }

    @Test
    public void createFolderRejectsDuplicateName() {
        FavoriteSnapshot s = new FavoriteSnapshot();
        assertTrue(s.createFolder("红利"));
        assertFalse("重名要被拒", s.createFolder("红利"));
        assertEquals(1, s.folders.size());
    }

    @Test
    public void createFolderRejectsBlankName() {
        FavoriteSnapshot s = new FavoriteSnapshot();
        assertFalse(s.createFolder(""));
        assertFalse(s.createFolder("   "));
        assertFalse(s.createFolder(null));
        assertTrue(s.folders.isEmpty());
    }

    @Test
    public void createFolderTrimsName() {
        FavoriteSnapshot s = new FavoriteSnapshot();
        assertTrue(s.createFolder("  红利  "));
        assertEquals(Collections.singletonList("红利"), s.folders);
    }

    /** 删夹只是整理动作，不该连带丢帖子：条目退回未分类 */
    @Test
    public void deleteFolderMovesItemsBackToUnfiled() {
        FavoriteSnapshot s = new FavoriteSnapshot();
        s.createFolder("红利");
        s.upsert(item(1));
        s.setFolder(1, "红利");

        s.deleteFolder("红利");

        assertTrue(s.folders.isEmpty());
        assertEquals("帖子不能跟着夹一起没", 1, s.items.size());
        assertEquals("", s.folderOf(1));
    }

    /**
     * 重命名要把夹内所有条目的 folder 一并改掉，
     * 否则会留下一批指向旧名、哪个夹都进不去的孤儿。
     */
    @Test
    public void renameFolderRetagsAllItems() {
        FavoriteSnapshot s = new FavoriteSnapshot();
        s.createFolder("红利");
        s.upsert(item(1));
        s.upsert(item(2));
        s.setFolder(1, "红利");
        s.setFolder(2, "红利");

        assertTrue(s.renameFolder("红利", "高股息"));

        assertEquals(Collections.singletonList("高股息"), s.folders);
        assertEquals("高股息", s.folderOf(1));
        assertEquals("高股息", s.folderOf(2));
    }

    @Test
    public void renameFolderRejectsDuplicateOrBlankTarget() {
        FavoriteSnapshot s = new FavoriteSnapshot();
        s.createFolder("红利");
        s.createFolder("银行");

        assertFalse(s.renameFolder("红利", "银行"));
        assertFalse(s.renameFolder("红利", "  "));
        assertEquals(Arrays.asList("红利", "银行"), s.folders);
    }

    @Test
    public void renameUnknownFolderReturnsFalse() {
        assertFalse(new FavoriteSnapshot().renameFolder("不存在", "新名"));
    }

    @Test
    public void deleteUnknownFolderIsNoOp() {
        FavoriteSnapshot s = new FavoriteSnapshot();
        s.createFolder("红利");
        s.deleteFolder("不存在");
        assertEquals(Collections.singletonList("红利"), s.folders);
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
export JAVA_HOME="D:/Program Files/Android/Android Studio/jbr" && ./gradlew.bat :nga_phone_base_3.0:testDebugUnitTest --tests "gov.anzong.androidnga.favorite.FavoriteSnapshotFolderTest" --console=plain
```

Expected: 编译失败，`cannot find symbol: method createFolder`

- [ ] **Step 3: 写实现（追加到 FavoriteSnapshot）**

```java
    /** 新建文件夹。空名、纯空格、重名一律拒绝并返回 false，由调用方提示用户 */
    public boolean createFolder(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || folders.contains(trimmed)) {
            return false;
        }
        folders.add(trimmed);
        return true;
    }

    /**
     * 重命名文件夹，并把夹内所有条目的 folder 一并改掉——
     * 漏了这一步就会留下一批指向旧名、哪个夹都进不去的孤儿条目。
     */
    public boolean renameFolder(String oldName, String newName) {
        String trimmed = newName == null ? "" : newName.trim();
        int index = folders.indexOf(oldName);
        if (index < 0 || trimmed.isEmpty() || folders.contains(trimmed)) {
            return false;
        }
        folders.set(index, trimmed);
        for (FavoriteItem item : items.values()) {
            if (oldName.equals(item.folder)) {
                item.folder = trimmed;
            }
        }
        return true;
    }

    /**
     * 删除文件夹。**帖子不删**，只是退回未分类——删夹是整理动作，
     * 用户不会期望连带丢掉收藏。
     */
    public void deleteFolder(String name) {
        if (!folders.remove(name)) {
            return;
        }
        for (FavoriteItem item : items.values()) {
            if (name.equals(item.folder)) {
                item.folder = "";
            }
        }
    }
```

- [ ] **Step 4: 跑测试确认通过**

```bash
export JAVA_HOME="D:/Program Files/Android/Android Studio/jbr" && ./gradlew.bat :nga_phone_base_3.0:testDebugUnitTest --tests "gov.anzong.androidnga.favorite.FavoriteSnapshotFolderTest" --console=plain
```

Expected: 9 个测试全部 PASS

- [ ] **Step 5: 提交**

```bash
git add nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/favorite/FavoriteSnapshot.java nga_phone_base_3.0/src/test/java/gov/anzong/androidnga/favorite/FavoriteSnapshotFolderTest.java
git commit -m "feat: 收藏快照的文件夹增删改"
```

---

### Task 3: 查询——按夹过滤、搜索、同步清理

**Files:**
- Modify: `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/favorite/FavoriteSnapshot.java`
- Test: `nga_phone_base_3.0/src/test/java/gov/anzong/androidnga/favorite/FavoriteSnapshotQueryTest.java`

**Interfaces:**
- Consumes: Task 1、2 的全部方法
- Produces: `List<FavoriteItem> itemsIn(String folder)`、`List<FavoriteItem> search(String keyword)`、`void pruneTo(Set<Integer> serverTids)`

- [ ] **Step 1: 写失败的测试**

```java
package gov.anzong.androidnga.favorite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.junit.Test;

public class FavoriteSnapshotQueryTest {

    private static FavoriteItem item(int tid, String subject, String author) {
        FavoriteItem i = new FavoriteItem();
        i.tid = tid;
        i.subject = subject;
        i.author = author;
        return i;
    }

    private static FavoriteSnapshot sample() {
        FavoriteSnapshot s = new FavoriteSnapshot();
        s.createFolder("红利");
        s.upsert(item(1, "工商银行分红方案", "张三"));
        s.upsert(item(2, "今天大盘怎么看", "李四"));
        s.upsert(item(3, "招商银行年报", "王五"));
        s.setFolder(1, "红利");
        return s;
    }

    /** 根视图只显示未分类，已归类的不能混进来 */
    @Test
    public void itemsInUnfiledExcludesFiled() {
        List<FavoriteItem> unfiled = sample().itemsIn("");
        assertEquals(2, unfiled.size());
        assertEquals(Arrays.asList(2, 3),
                Arrays.asList(unfiled.get(0).tid, unfiled.get(1).tid));
    }

    @Test
    public void itemsInFolderReturnsOnlyThatFolder() {
        List<FavoriteItem> filed = sample().itemsIn("红利");
        assertEquals(1, filed.size());
        assertEquals(1, filed.get(0).tid);
    }

    @Test
    public void itemsInUnknownFolderIsEmpty() {
        assertTrue(sample().itemsIn("不存在的夹").isEmpty());
    }

    /** 搜索跨文件夹：归到「红利」里的帖子也要能被搜到 */
    @Test
    public void searchCrossesFolders() {
        List<FavoriteItem> result = sample().search("银行");
        assertEquals(2, result.size());
        assertEquals(Arrays.asList(1, 3),
                Arrays.asList(result.get(0).tid, result.get(1).tid));
    }

    @Test
    public void searchMatchesAuthor() {
        List<FavoriteItem> result = sample().search("李四");
        assertEquals(1, result.size());
        assertEquals(2, result.get(0).tid);
    }

    @Test
    public void searchIsCaseInsensitiveAndTrimmed() {
        FavoriteSnapshot s = new FavoriteSnapshot();
        s.upsert(item(1, "ETF 定投", "Tom"));
        assertEquals(1, s.search("  etf  ").size());
        assertEquals(1, s.search("TOM").size());
    }

    @Test
    public void blankKeywordReturnsNothing() {
        assertTrue(sample().search("").isEmpty());
        assertTrue(sample().search("   ").isEmpty());
        assertTrue(sample().search(null).isEmpty());
    }

    /** 只有「同步全部收藏」完整成功后才调 pruneTo，清掉网页端已取消收藏的孤儿 */
    @Test
    public void pruneToRemovesItemsMissingFromServer() {
        FavoriteSnapshot s = sample();
        s.pruneTo(new HashSet<>(Arrays.asList(1, 3)));

        assertEquals(2, s.items.size());
        assertEquals("红利", s.folderOf(1));
        assertEquals("", s.folderOf(2));
    }

    /** 兜底：服务端全集为空时不清库，多半是拉取异常而不是真的一条收藏都没有 */
    @Test
    public void pruneToIgnoresEmptyServerSet() {
        FavoriteSnapshot s = sample();
        s.pruneTo(new HashSet<>());
        assertEquals(3, s.items.size());
    }

    /** 文件夹不因为里面的帖子被清空而消失 */
    @Test
    public void pruneToKeepsFolders() {
        FavoriteSnapshot s = sample();
        s.pruneTo(new HashSet<>(Arrays.asList(2, 3)));
        assertEquals(Collections.singletonList("红利"), s.folders);
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
export JAVA_HOME="D:/Program Files/Android/Android Studio/jbr" && ./gradlew.bat :nga_phone_base_3.0:testDebugUnitTest --tests "gov.anzong.androidnga.favorite.FavoriteSnapshotQueryTest" --console=plain
```

Expected: 编译失败，`cannot find symbol: method itemsIn`

- [ ] **Step 3: 写实现**

先在 `FavoriteSnapshot.java` 顶部补 import：

```java
import java.util.Locale;
import java.util.Set;
```

再追加方法：

```java
    /**
     * 取某个文件夹里的条目，传空串取未分类。
     * 返回顺序沿用 items 的插入顺序（LinkedHashMap），也就是服务端列表的先后。
     */
    public List<FavoriteItem> itemsIn(String folder) {
        String target = folder == null ? "" : folder;
        List<FavoriteItem> result = new ArrayList<>();
        for (FavoriteItem item : items.values()) {
            if (target.equals(item.folder)) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * 按标题或作者搜索，**不分文件夹**——搜的时候通常不记得当初把帖子放哪了。
     * 关键词为空返回空列表，由调用方据此退回普通视图。
     */
    public List<FavoriteItem> search(String keyword) {
        List<FavoriteItem> result = new ArrayList<>();
        String key = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty()) {
            return result;
        }
        for (FavoriteItem item : items.values()) {
            String subject = item.subject == null ? "" : item.subject.toLowerCase(Locale.ROOT);
            String author = item.author == null ? "" : item.author.toLowerCase(Locale.ROOT);
            if (subject.contains(key) || author.contains(key)) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * 按服务端返回的收藏全集清理本地孤儿条目（在网页端取消收藏的那些）。
     *
     * **只能在「同步全部收藏」完整成功后调用。**中途失败时调用会把没拉到的
     * 收藏当成已取消一并删掉。全集为空时直接跳过，那多半是拉取异常而不是
     * 真的一条收藏都没有。
     */
    public void pruneTo(Set<Integer> serverTids) {
        if (serverTids == null || serverTids.isEmpty()) {
            return;
        }
        items.keySet().removeIf(key -> !serverTids.contains(Integer.valueOf(key)));
    }
```

- [ ] **Step 4: 跑测试确认通过**

```bash
export JAVA_HOME="D:/Program Files/Android/Android Studio/jbr" && ./gradlew.bat :nga_phone_base_3.0:testDebugUnitTest --tests "gov.anzong.androidnga.favorite.FavoriteSnapshotQueryTest" --console=plain
```

Expected: 10 个测试全部 PASS

- [ ] **Step 5: 提交**

```bash
git add nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/favorite/FavoriteSnapshot.java nga_phone_base_3.0/src/test/java/gov/anzong/androidnga/favorite/FavoriteSnapshotQueryTest.java
git commit -m "feat: 收藏快照的过滤、搜索与同步清理"
```

---

### Task 4: FavoriteStore 持久化

**Files:**
- Create: `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/favorite/FavoriteStore.java`
- Test: `nga_phone_base_3.0/src/test/java/gov/anzong/androidnga/favorite/FavoriteStoreParseTest.java`

**Interfaces:**
- Consumes: Task 1–3 的 `FavoriteSnapshot`、`FavoriteItem`
- Produces: `static FavoriteStore getInstance()`、`FavoriteSnapshot snapshot()`、`void load()`、`void save()`、`void upsertAll(List<ThreadPageInfo>)`、`static FavoriteSnapshot parse(String json)`

`parse` 做成静态纯函数，这样解析容错能脱离 Android 单测。

- [ ] **Step 1: 写失败的测试**

```java
package gov.anzong.androidnga.favorite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FavoriteStoreParseTest {

    /** 正常读回：文件夹顺序和条目的 folder 都要还原 */
    @Test
    public void parseRestoresFoldersAndItems() {
        String json = "{\"folders\":[\"红利\",\"银行\"],"
                + "\"items\":{\"1\":{\"tid\":1,\"subject\":\"标题\",\"author\":\"张三\","
                + "\"folder\":\"红利\",\"replies\":7}}}";

        FavoriteSnapshot s = FavoriteStore.parse(json);

        assertEquals(2, s.folders.size());
        assertEquals("红利", s.folders.get(0));
        assertEquals("红利", s.folderOf(1));
        assertEquals("标题", s.items.get("1").subject);
        assertEquals(7, s.items.get("1").replies);
    }

    /** 文件损坏时当空库重建，绝不能崩——快照是增强，不是主数据源 */
    @Test
    public void parseCorruptJsonReturnsEmptySnapshot() {
        FavoriteSnapshot s = FavoriteStore.parse("{这不是合法 json");
        assertNotNull(s);
        assertTrue(s.folders.isEmpty());
        assertTrue(s.items.isEmpty());
    }

    @Test
    public void parseEmptyOrNullReturnsEmptySnapshot() {
        assertTrue(FavoriteStore.parse("").items.isEmpty());
        assertTrue(FavoriteStore.parse(null).items.isEmpty());
    }

    /** 老文件缺字段时补默认值，不能让 folder 变成 null */
    @Test
    public void parseFillsMissingFolderWithEmptyString() {
        String json = "{\"items\":{\"5\":{\"tid\":5,\"subject\":\"没有 folder 字段\"}}}";
        assertEquals("", FavoriteStore.parse(json).folderOf(5));
    }

    /** 指向已不存在文件夹的条目，读回时退回未分类，避免点不进去的孤儿 */
    @Test
    public void parseResetsItemsPointingToMissingFolder() {
        String json = "{\"folders\":[\"银行\"],"
                + "\"items\":{\"6\":{\"tid\":6,\"folder\":\"已删掉的夹\"}}}";
        assertEquals("", FavoriteStore.parse(json).folderOf(6));
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

```bash
export JAVA_HOME="D:/Program Files/Android/Android Studio/jbr" && ./gradlew.bat :nga_phone_base_3.0:testDebugUnitTest --tests "gov.anzong.androidnga.favorite.FavoriteStoreParseTest" --console=plain
```

Expected: 编译失败，`cannot find symbol: class FavoriteStore`

- [ ] **Step 3: 写实现**

```java
package gov.anzong.androidnga.favorite;

import com.alibaba.fastjson.JSON;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import gov.anzong.androidnga.base.util.ContextUtils;
import gov.anzong.androidnga.base.util.ThreadUtils;
import sp.phone.mvp.model.entity.ThreadPageInfo;

/**
 * 本地收藏快照的持久化。
 *
 * NGA 的收藏夹在服务端且接口没有文件夹概念，本地这份快照是搜索和文件夹功能的
 * 唯一数据来源。它是**增强而非主数据源**：文件损坏、写入失败都只降级，
 * 不影响收藏列表本身的浏览。
 *
 * 单例常驻内存，写盘走子线程。
 */
public class FavoriteStore {

    private static final String TAG = "FavoriteStore";

    private static final String FILE_NAME = "favorite_snapshot.json";

    private static final FavoriteStore sInstance = new FavoriteStore();

    private FavoriteSnapshot mSnapshot = new FavoriteSnapshot();

    private boolean mLoaded;

    private FavoriteStore() {
    }

    public static FavoriteStore getInstance() {
        return sInstance;
    }

    public FavoriteSnapshot snapshot() {
        return mSnapshot;
    }

    private static File file() {
        return new File(ContextUtils.getContext().getFilesDir(), FILE_NAME);
    }

    /** 读盘。进入收藏夹界面时调一次，之后都用内存里的 */
    public void load() {
        if (mLoaded) {
            return;
        }
        mLoaded = true;
        try {
            File f = file();
            mSnapshot = f.exists() ? parse(FileUtils.readFileToString(f)) : new FavoriteSnapshot();
        } catch (Exception e) {
            System.err.println(TAG + " load failed, start empty: " + e);
            mSnapshot = new FavoriteSnapshot();
        }
    }

    /** 写盘。失败只记日志——丢一次快照不该打断用户浏览收藏 */
    public void save() {
        final String json = JSON.toJSONString(mSnapshot);
        ThreadUtils.postOnSubThread(() -> {
            try {
                FileUtils.write(file(), json);
            } catch (Exception e) {
                System.err.println(TAG + " save failed: " + e);
            }
        });
    }

    /**
     * 解析快照文件。做成静态纯函数是为了让容错逻辑能脱离 Android 单测，
     * 所以这里用 System.err 而不是 NLog——NLog 依赖 Android，单测里跑不起来。
     *
     * 任何解析失败都返回空库而不是抛异常；缺字段补默认值；
     * 指向已不存在文件夹的条目退回未分类，避免留下点不进去的孤儿。
     */
    public static FavoriteSnapshot parse(String json) {
        FavoriteSnapshot parsed = null;
        if (json != null && !json.trim().isEmpty()) {
            try {
                parsed = JSON.parseObject(json, FavoriteSnapshot.class);
            } catch (Exception e) {
                System.err.println(TAG + " parse failed, start empty: " + e);
            }
        }
        if (parsed == null) {
            return new FavoriteSnapshot();
        }
        if (parsed.folders == null) {
            parsed.folders = new ArrayList<>();
        }
        if (parsed.items == null) {
            parsed.items = new LinkedHashMap<>();
        }
        for (FavoriteItem item : parsed.items.values()) {
            if (item.folder == null || !parsed.folders.contains(item.folder)) {
                item.folder = "";
            }
        }
        return parsed;
    }

    /**
     * 把服务端返回的一页收藏写进快照。只加不删，且不动已有的 folder，
     * 规则见 FavoriteSnapshot#upsert。
     */
    public void upsertAll(List<ThreadPageInfo> pageList) {
        if (pageList == null || pageList.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (ThreadPageInfo info : pageList) {
            FavoriteItem item = new FavoriteItem();
            item.tid = info.getTid();
            item.subject = info.getSubject() == null ? "" : info.getSubject();
            item.author = info.getAuthor() == null ? "" : info.getAuthor();
            item.fid = info.getFid();
            item.board = info.getBoard() == null ? "" : info.getBoard();
            item.replies = info.getReplies();
            item.postDate = info.getPostDate();
            item.updateTime = now;
            mSnapshot.upsert(item);
        }
        save();
    }
}
```

`parse` 里那个 `item.folder = ""` 分支同时覆盖「folder 为 null」和「folder 指向已删掉的夹」两种情况，测试 4 和测试 5 都走它。

- [ ] **Step 4: 跑测试确认通过**

```bash
export JAVA_HOME="D:/Program Files/Android/Android Studio/jbr" && ./gradlew.bat :nga_phone_base_3.0:testDebugUnitTest --tests "gov.anzong.androidnga.favorite.FavoriteStoreParseTest" --console=plain
```

Expected: 5 个测试全部 PASS

- [ ] **Step 5: 提交**

```bash
git add nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/favorite/FavoriteStore.java nga_phone_base_3.0/src/test/java/gov/anzong/androidnga/favorite/FavoriteStoreParseTest.java
git commit -m "feat: 收藏快照的 JSON 持久化与容错解析"
```

---

### Task 5: 收藏夹布局与根视图只显示未分类

**Files:**
- Create: `nga_phone_base_3.0/src/main/res/layout/fragment_topic_favorite.xml`
- Modify: `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicFavoriteFragment.java`

**Interfaces:**
- Consumes: Task 4 的 `FavoriteStore.getInstance()`、`load()`、`upsertAll()`、`snapshot()`
- Produces: `TopicFavoriteFragment` 的字段 `String mCurrentFolder`（空串 = 根/未分类）、`LinearLayout mFolderStrip`、`View mFolderScroll`；方法 `renderFolderStrip()`、`filterForCurrentView(List<ThreadPageInfo>)`

- [ ] **Step 1: 建新布局**

在 `fragment_topic_list.xml` 基础上，于 `AppBarLayout` 里 Toolbar 下方插入一条横向滚动的文件夹区。**`swipe_refresh`、`list` 两个 id 以及 `list_empty_view` / `list_loading_view` 两个 include 必须原样保留**，父类 `TopicSearchFragment` 的 ButterKnife 绑定依赖它们，少一个就 NPE。

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/main_content"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fitsSystemWindows="true">

    <com.google.android.material.appbar.AppBarLayout
        android:id="@+id/appbar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:theme="@style/AppTheme.AppBarOverlay">

        <androidx.appcompat.widget.Toolbar
            android:id="@+id/toolbar"
            android:layout_width="match_parent"
            android:layout_height="?attr/actionBarSize"
            app:popupTheme="@style/AppTheme.PopupOverlay" />

        <!-- 文件夹区：横向滚动一行。文件夹多了往右滑，不换行挤压下方列表 -->
        <HorizontalScrollView
            android:id="@+id/folder_scroll"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:scrollbars="none">

            <LinearLayout
                android:id="@+id/folder_strip"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:orientation="horizontal"
                android:paddingStart="8dp"
                android:paddingEnd="8dp"
                android:paddingTop="4dp"
                android:paddingBottom="4dp" />
        </HorizontalScrollView>

    </com.google.android.material.appbar.AppBarLayout>

    <androidx.swiperefreshlayout.widget.SwipeRefreshLayout
        android:id="@+id/swipe_refresh"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:layout_behavior="@string/appbar_scrolling_view_behavior">

        <FrameLayout
            android:layout_width="match_parent"
            android:layout_height="match_parent">

            <sp.phone.view.RecyclerViewEx
                android:id="@+id/list"
                android:layout_width="match_parent"
                android:layout_height="match_parent" />

            <include layout="@layout/list_empty_view" />

        </FrameLayout>
    </androidx.swiperefreshlayout.widget.SwipeRefreshLayout>

    <include layout="@layout/list_loading_view" />

</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

- [ ] **Step 2: 覆写 onCreateView 并接上 store**

`TopicFavoriteFragment.java` 全量替换为：

```java
package sp.phone.ui.fragment;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import gov.anzong.androidnga.R;
import gov.anzong.androidnga.favorite.FavoriteStore;
import sp.phone.mvp.model.entity.ThreadPageInfo;
import sp.phone.mvp.model.entity.TopicListInfo;

/**
 * 收藏夹。
 *
 * 服务端只给一个平铺的收藏列表，文件夹是本地的（见 FavoriteStore）。
 * 界面按文件管理器的样子组织：上面一行文件夹，下面直接是未分类的帖子。
 */
public class TopicFavoriteFragment extends TopicSearchFragment implements View.OnLongClickListener {

    /** 当前所在文件夹，空串表示根视图（显示未分类的帖子） */
    private String mCurrentFolder = "";

    private LinearLayout mFolderStrip;

    private View mFolderScroll;

    @Override
    protected void setTitle() {
        setTitle(R.string.bookmark_title);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_topic_favorite, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mFolderStrip = view.findViewById(R.id.folder_strip);
        mFolderScroll = view.findViewById(R.id.folder_scroll);
        FavoriteStore.getInstance().load();
        mAdapter.setOnLongClickListener(this);
        mPresenter.getRemovedTopic().observe(this, this::removeTopic);
        renderFolderStrip();
    }

    /**
     * 服务端每来一页收藏就写进本地快照，然后**只把当前视图该看到的那部分交给适配器**。
     *
     * 父类是直接把整页塞给适配器的，这里必须拦下来：根视图只显示未分类的帖子，
     * 已经归了类的应该只在对应文件夹里出现。
     */
    @Override
    public void setData(TopicListInfo result) {
        mTopicListInfo = result;
        List<ThreadPageInfo> pageList = result.getThreadPageList();
        FavoriteStore.getInstance().upsertAll(pageList);
        mAdapter.setData(filterForCurrentView(pageList));
    }

    private List<ThreadPageInfo> filterForCurrentView(List<ThreadPageInfo> pageList) {
        List<ThreadPageInfo> visible = new ArrayList<>();
        if (pageList == null) {
            return visible;
        }
        for (ThreadPageInfo info : pageList) {
            String folder = FavoriteStore.getInstance().snapshot().folderOf(info.getTid());
            if (mCurrentFolder.equals(folder)) {
                visible.add(info);
            }
        }
        return visible;
    }

    /** 文件夹区：一个都没建时整块隐藏，不白占一条高度 */
    private void renderFolderStrip() {
        List<String> folders = FavoriteStore.getInstance().snapshot().folders;
        mFolderStrip.removeAllViews();
        if (folders.isEmpty()) {
            mFolderScroll.setVisibility(View.GONE);
            return;
        }
        mFolderScroll.setVisibility(View.VISIBLE);
        for (String folder : folders) {
            mFolderStrip.addView(createFolderChip(folder));
        }
    }

    private TextView createFolderChip(final String folder) {
        TextView chip = new TextView(getContext());
        chip.setText("📁 " + folder);
        chip.setTextSize(14);
        chip.setPadding(24, 12, 24, 12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = 12;
        chip.setLayoutParams(lp);
        return chip;
    }

    @Override
    public void removeTopic(int position) {
        mAdapter.removeItem(position);
    }

    @Override
    public void removeTopic(ThreadPageInfo pageInfo) {
        mAdapter.removeItem(pageInfo);
    }

    @Override
    public boolean onLongClick(final View view) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setMessage(this.getString(R.string.delete_favo_confirm_text))
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ThreadPageInfo info = (ThreadPageInfo) view.getTag();
                        mPresenter.removeTopic(info, info.getPosition());
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create()
                .show();
        return true;
    }
}
```

原来 `onViewCreated` 里那句 `ToastUtils.info("长按可删除收藏的帖子")` 在此删除——Task 6 会把长按改成菜单，那句提示届时不再准确，现在先去掉免得漏掉。

- [ ] **Step 3: 编译并装机**

```bash
export JAVA_HOME="D:/Program Files/Android/Android Studio/jbr" && ./gradlew.bat :nga_phone_base_3.0:assembleDebug --console=plain -q 2>&1 | grep -E "^e: |error:" | head
```

```bash
C:/Users/codea/AppData/Local/Android/Sdk/platform-tools/adb.exe -s 374d7eee install -r nga_phone_base_3.0/build/outputs/apk/debug/nga_phone_base_3.0-debug.apk
```

- [ ] **Step 4: 手测**

进「我的 → 收藏夹」。预期：列表和改动前完全一样（此时还没有任何文件夹，所有帖子都算未分类），文件夹区不可见；长按仍然弹删除确认。

- [ ] **Step 5: 提交**

```bash
git add nga_phone_base_3.0/src/main/res/layout/fragment_topic_favorite.xml nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicFavoriteFragment.java
git commit -m "feat: 收藏夹接入本地快照，根视图只显示未分类"
```

---

### Task 6: 长按帖子弹菜单 + 移到文件夹

**Files:**
- Modify: `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicFavoriteFragment.java`

**Interfaces:**
- Consumes: Task 5 的 `mCurrentFolder`、`renderFolderStrip()`；Task 2 的 `createFolder`；Task 1 的 `setFolder`
- Produces: `confirmRemove(ThreadPageInfo)`、`showMoveDialog(ThreadPageInfo)`、`showCreateFolderDialog(ThreadPageInfo)`、`moveTo(ThreadPageInfo, String)`

- [ ] **Step 1: 把 onLongClick 改成菜单**

顶部补 import：

```java
import android.widget.EditText;

import gov.anzong.androidnga.base.util.ToastUtils;
```

用下面五个方法替换原来的 `onLongClick`：

```java
    /**
     * 长按出菜单而不是直接弹删除确认。
     *
     * 归类和删除都挂在长按上，而且直接删除太容易误触——取消收藏是不可逆的。
     */
    @Override
    public boolean onLongClick(final View view) {
        final ThreadPageInfo info = (ThreadPageInfo) view.getTag();
        new AlertDialog.Builder(getContext())
                .setTitle(info.getSubject())
                .setItems(new CharSequence[]{"移到文件夹", "删除收藏"}, (dialog, which) -> {
                    if (which == 0) {
                        showMoveDialog(info);
                    } else {
                        confirmRemove(info);
                    }
                })
                .show();
        return true;
    }

    private void confirmRemove(final ThreadPageInfo info) {
        new AlertDialog.Builder(getContext())
                .setMessage(getString(R.string.delete_favo_confirm_text))
                .setPositiveButton(android.R.string.ok,
                        (dialog, which) -> mPresenter.removeTopic(info, info.getPosition()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** 单选：现有文件夹 + 新建 + 移出。一个帖子只属于一个文件夹，所以是单选 */
    private void showMoveDialog(final ThreadPageInfo info) {
        final List<String> folders =
                new ArrayList<>(FavoriteStore.getInstance().snapshot().folders);
        final CharSequence[] options = new CharSequence[folders.size() + 2];
        for (int i = 0; i < folders.size(); i++) {
            options[i] = folders.get(i);
        }
        options[folders.size()] = "新建文件夹…";
        options[folders.size() + 1] = "移出文件夹";

        new AlertDialog.Builder(getContext())
                .setTitle("移到文件夹")
                .setItems(options, (dialog, which) -> {
                    if (which == folders.size()) {
                        showCreateFolderDialog(info);
                    } else if (which == folders.size() + 1) {
                        moveTo(info, "");
                    } else {
                        moveTo(info, folders.get(which));
                    }
                })
                .show();
    }

    private void showCreateFolderDialog(final ThreadPageInfo info) {
        final EditText input = new EditText(getContext());
        input.setHint("文件夹名，如 红利");
        new AlertDialog.Builder(getContext())
                .setTitle("新建文件夹")
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!FavoriteStore.getInstance().snapshot().createFolder(name)) {
                        ToastUtils.error(name.isEmpty() ? "文件夹名不能为空" : "已存在同名文件夹");
                        return;
                    }
                    moveTo(info, name);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * 归类后立刻把该帖从当前列表移除——它已经不属于这个视图了，
     * 留在原地会让人以为没生效。
     */
    private void moveTo(ThreadPageInfo info, String folder) {
        FavoriteStore.getInstance().snapshot().setFolder(info.getTid(), folder);
        FavoriteStore.getInstance().save();
        renderFolderStrip();
        mAdapter.removeItem(info);
        ToastUtils.success(folder.isEmpty() ? "已移出文件夹" : "已移到「" + folder + "」");
    }
```

- [ ] **Step 2: 编译并装机**

```bash
export JAVA_HOME="D:/Program Files/Android/Android Studio/jbr" && ./gradlew.bat :nga_phone_base_3.0:assembleDebug --console=plain -q 2>&1 | grep -E "^e: |error:" | head
```

```bash
C:/Users/codea/AppData/Local/Android/Sdk/platform-tools/adb.exe -s 374d7eee install -r nga_phone_base_3.0/build/outputs/apk/debug/nga_phone_base_3.0-debug.apk
```

- [ ] **Step 3: 手测**

1. 长按一个帖子 → 出现「移到文件夹 / 删除收藏」
2. 选「移到文件夹 → 新建文件夹… → 红利」→ 该帖从列表消失，顶部出现「📁 红利」
3. 再新建一个同名「红利」→ 提示「已存在同名文件夹」
4. 长按另一个帖子 → 移到「红利」→ 也从列表消失
5. **杀掉进程重进收藏夹**：文件夹「红利」还在，那两个帖子仍不在根列表（验证落盘）
6. **下拉刷新收藏夹** → 那两个帖子仍然不在根列表（验证 upsert 没冲掉 folder，这是最关键的一条）
7. 长按 → 删除收藏 → 仍弹确认框，确认后帖子消失

- [ ] **Step 4: 提交**

```bash
git add nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicFavoriteFragment.java
git commit -m "feat: 长按收藏帖弹菜单，支持归类到文件夹"
```

---

### Task 7: 点开文件夹就地切换 + 返回

**Files:**
- Modify: `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicFavoriteFragment.java`
- Modify: `nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/TopicListActivity.java`

**Interfaces:**
- Consumes: Task 5、6 的全部成员
- Produces: `enterFolder(String)`、`backToRoot()`、`reloadCurrentView()`、`public boolean onBackPressedHandled()`

- [ ] **Step 1: 实现就地切换**

`createFolderChip` 末尾（`return chip;` 之前）加一行：

```java
        chip.setOnClickListener(v -> enterFolder(folder));
```

新增四个方法：

```java
    private void enterFolder(String folder) {
        mCurrentFolder = folder;
        setTitle("收藏夹 - " + folder);
        renderFolderStrip();
        reloadCurrentView();
    }

    private void backToRoot() {
        mCurrentFolder = "";
        setTitle(getString(R.string.bookmark_title));
        renderFolderStrip();
        reloadCurrentView();
    }

    /** 供宿主 Activity 的返回键调用：在文件夹里就先退回根视图，返回 true 表示已消费 */
    public boolean onBackPressedHandled() {
        if (mCurrentFolder.isEmpty()) {
            return false;
        }
        backToRoot();
        return true;
    }

    /**
     * 切换视图时重新过滤**已经加载到的**那些帖子，不重新请求服务端——
     * 切个文件夹就重拉一遍收藏夹，正是会触发限流的那种行为。
     */
    private void reloadCurrentView() {
        mAdapter.setData(null);
        if (mTopicListInfo != null) {
            mAdapter.setData(filterForCurrentView(mTopicListInfo.getThreadPageList()));
        }
    }
```

`renderFolderStrip()` 改成区分根/夹内两种形态：

```java
    private void renderFolderStrip() {
        List<String> folders = FavoriteStore.getInstance().snapshot().folders;
        mFolderStrip.removeAllViews();

        if (!mCurrentFolder.isEmpty()) {
            // 夹内：整条换成返回入口
            TextView back = new TextView(getContext());
            back.setText("← " + mCurrentFolder);
            back.setTextSize(14);
            back.setPadding(24, 12, 24, 12);
            back.setOnClickListener(v -> backToRoot());
            mFolderScroll.setVisibility(View.VISIBLE);
            mFolderStrip.addView(back);
            return;
        }

        if (folders.isEmpty()) {
            mFolderScroll.setVisibility(View.GONE);
            return;
        }
        mFolderScroll.setVisibility(View.VISIBLE);
        for (String folder : folders) {
            mFolderStrip.addView(createFolderChip(folder));
        }
    }
```

- [ ] **Step 2: 接系统返回键**

`TopicListActivity:103` 用的容器是 `android.R.id.content`（不是自定义 id），照此在 `TopicListActivity` 里加：

```java
    @Override
    public void onBackPressed() {
        androidx.fragment.app.Fragment fragment =
                getSupportFragmentManager().findFragmentById(android.R.id.content);
        if (fragment instanceof TopicFavoriteFragment
                && ((TopicFavoriteFragment) fragment).onBackPressedHandled()) {
            return;
        }
        super.onBackPressed();
    }
```

- [ ] **Step 3: 编译并装机**

```bash
export JAVA_HOME="D:/Program Files/Android/Android Studio/jbr" && ./gradlew.bat :nga_phone_base_3.0:assembleDebug --console=plain -q 2>&1 | grep -E "^e: |error:" | head
```

```bash
C:/Users/codea/AppData/Local/Android/Sdk/platform-tools/adb.exe -s 374d7eee install -r nga_phone_base_3.0/build/outputs/apk/debug/nga_phone_base_3.0-debug.apk
```

- [ ] **Step 4: 手测**

1. 点「📁 红利」→ 顶部变「← 红利」，列表只剩归到红利的帖子，标题变「收藏夹 - 红利」
2. 点「← 红利」→ 回到根视图，列表是未分类的
3. 进夹后按系统返回键 → 退回根视图而不是退出页面；在根视图再按 → 退出页面
4. 打开一个空文件夹 → 列表为空（空状态文案在 Task 10 补）

- [ ] **Step 5: 提交**

```bash
git add nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicFavoriteFragment.java nga_phone_base_3.0/src/main/java/gov/anzong/androidnga/activity/TopicListActivity.java
git commit -m "feat: 收藏夹文件夹就地进出与返回键处理"
```

---

### Task 8: 长按文件夹重命名/删除

**Files:**
- Modify: `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicFavoriteFragment.java`

**Interfaces:**
- Consumes: Task 2 的 `renameFolder`、`deleteFolder`；Task 7 的 `renderFolderStrip`、`reloadCurrentView`、`backToRoot`
- Produces: `showFolderMenu(String)`、`showRenameFolderDialog(String)`、`confirmDeleteFolder(String)`

- [ ] **Step 1: 给 chip 加长按**

`createFolderChip` 里（`return chip;` 之前）补：

```java
        chip.setOnLongClickListener(v -> {
            showFolderMenu(folder);
            return true;
        });
```

新增三个方法：

```java
    private void showFolderMenu(final String folder) {
        new AlertDialog.Builder(getContext())
                .setTitle(folder)
                .setItems(new CharSequence[]{"重命名", "删除文件夹"}, (dialog, which) -> {
                    if (which == 0) {
                        showRenameFolderDialog(folder);
                    } else {
                        confirmDeleteFolder(folder);
                    }
                })
                .show();
    }

    private void showRenameFolderDialog(final String folder) {
        final EditText input = new EditText(getContext());
        input.setText(folder);
        new AlertDialog.Builder(getContext())
                .setTitle("重命名文件夹")
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!FavoriteStore.getInstance().snapshot().renameFolder(folder, name)) {
                        ToastUtils.error(name.isEmpty() ? "文件夹名不能为空" : "已存在同名文件夹");
                        return;
                    }
                    FavoriteStore.getInstance().save();
                    if (mCurrentFolder.equals(folder)) {
                        mCurrentFolder = name;
                        setTitle("收藏夹 - " + name);
                    }
                    renderFolderStrip();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * 删夹只是把里面的帖子放回未分类，对话框必须写清楚这点，
     * 否则用户会以为连收藏一起删了而不敢用。
     */
    private void confirmDeleteFolder(final String folder) {
        new AlertDialog.Builder(getContext())
                .setTitle("删除文件夹「" + folder + "」")
                .setMessage("里面的帖子会回到未分类，收藏本身不会被删除。")
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    FavoriteStore.getInstance().snapshot().deleteFolder(folder);
                    FavoriteStore.getInstance().save();
                    if (mCurrentFolder.equals(folder)) {
                        backToRoot();
                    } else {
                        renderFolderStrip();
                        reloadCurrentView();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
```

- [ ] **Step 2: 编译并装机**

```bash
export JAVA_HOME="D:/Program Files/Android/Android Studio/jbr" && ./gradlew.bat :nga_phone_base_3.0:assembleDebug --console=plain -q 2>&1 | grep -E "^e: |error:" | head
```

```bash
C:/Users/codea/AppData/Local/Android/Sdk/platform-tools/adb.exe -s 374d7eee install -r nga_phone_base_3.0/build/outputs/apk/debug/nga_phone_base_3.0-debug.apk
```

- [ ] **Step 3: 手测**

1. 长按「📁 红利」→ 出「重命名 / 删除文件夹」
2. 重命名成「高股息」→ chip 变了；进去看，之前归类的帖子都还在（验证条目一并改名）
3. 删除「高股息」→ 对话框说明帖子会回到未分类；确认后 chip 消失，那些帖子重新出现在根列表
4. 在夹内时删掉当前夹 → 自动退回根视图，不会停在一个已不存在的夹里

- [ ] **Step 4: 提交**

```bash
git add nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicFavoriteFragment.java
git commit -m "feat: 长按文件夹支持重命名与删除"
```

---

### Task 9: 搜索

**Files:**
- Create: `nga_phone_base_3.0/src/main/res/menu/menu_topic_favorite.xml`
- Modify: `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicFavoriteFragment.java`

**Interfaces:**
- Consumes: Task 3 的 `search(String)`；Task 7 的 `renderFolderStrip`、`reloadCurrentView`
- Produces: 字段 `String mSearchKeyword`；方法 `renderSearchResult(String)`；菜单项 id `menu_search`、`menu_sync_all_favorite`

- [ ] **Step 1: 建菜单资源**

既有的 `menu_search.xml` 只有搜索项，这里还要放 Task 10 的「同步全部收藏」，所以单独建一个：

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android"
      xmlns:app="http://schemas.android.com/apk/res-auto">

    <item
        android:id="@+id/menu_search"
        android:title="@string/search"
        app:actionViewClass="androidx.appcompat.widget.SearchView"
        app:showAsAction="always|collapseActionView" />

    <item
        android:id="@+id/menu_sync_all_favorite"
        android:title="同步全部收藏"
        app:showAsAction="never" />

</menu>
```

- [ ] **Step 2: 接上 SearchView**

顶部补 import：

```java
import android.view.Menu;
import android.view.MenuInflater;

import androidx.appcompat.widget.SearchView;

import gov.anzong.androidnga.favorite.FavoriteItem;
```

新增字段和方法：

```java
    /** 非空表示正在搜索，此时无视 mCurrentFolder，展示全部收藏里的匹配项 */
    private String mSearchKeyword = "";

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_topic_favorite, menu);
        SearchView searchView = (SearchView) menu.findItem(R.id.menu_search).getActionView();
        searchView.setQueryHint("搜索收藏的帖子");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                renderSearchResult(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                renderSearchResult(newText);
                return true;
            }
        });
        super.onCreateOptionsMenu(menu, inflater);
    }

    /**
     * 搜索**跨文件夹**：搜的时候通常不记得当初把帖子放哪了。
     *
     * 搜索期间隐藏文件夹区——结果是跨文件夹的平铺列表，此时留着文件夹区
     * 会让人误以为搜索被限定在某个夹里。清空关键词后恢复到搜索前的视图。
     */
    private void renderSearchResult(String keyword) {
        mSearchKeyword = keyword == null ? "" : keyword.trim();
        if (mSearchKeyword.isEmpty()) {
            renderFolderStrip();
            reloadCurrentView();
            return;
        }
        mFolderScroll.setVisibility(View.GONE);

        List<FavoriteItem> hits = FavoriteStore.getInstance().snapshot().search(mSearchKeyword);
        List<ThreadPageInfo> rows = new ArrayList<>();
        for (FavoriteItem hit : hits) {
            ThreadPageInfo info = new ThreadPageInfo();
            info.setTid(hit.tid);
            // 已分类的在标题后缀出所属文件夹，省得还要点进去才知道放哪了
            info.setSubject(hit.folder.isEmpty()
                    ? hit.subject : hit.subject + "  [" + hit.folder + "]");
            info.setAuthor(hit.author);
            info.setFid(hit.fid);
            info.setBoard(hit.board);
            info.setReplies(hit.replies);
            info.setPostDate(hit.postDate);
            rows.add(info);
        }
        mAdapter.setData(null);
        mAdapter.setData(rows);
    }
```

- [ ] **Step 3: 编译并装机**

```bash
export JAVA_HOME="D:/Program Files/Android/Android Studio/jbr" && ./gradlew.bat :nga_phone_base_3.0:assembleDebug --console=plain -q 2>&1 | grep -E "^e: |error:" | head
```

```bash
C:/Users/codea/AppData/Local/Android/Sdk/platform-tools/adb.exe -s 374d7eee install -r nga_phone_base_3.0/build/outputs/apk/debug/nga_phone_base_3.0-debug.apk
```

- [ ] **Step 4: 手测**

1. 右上角出现 🔍，点开输入「银行」→ 边打边过滤
2. 归到「红利」里的帖子也出现在结果里，标题后带 `[红利]`
3. 清空关键词 → 回到搜索前的视图（根视图或所在文件夹），文件夹区重新出现
4. 在文件夹里搜索 → 结果同样跨全部收藏，不被当前夹限制
5. 点搜索结果里的帖子 → 能正常打开（验证 `tid` 传对了）

- [ ] **Step 5: 提交**

```bash
git add nga_phone_base_3.0/src/main/res/menu/menu_topic_favorite.xml nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicFavoriteFragment.java
git commit -m "feat: 收藏夹本地搜索，跨文件夹并标注所属夹"
```

---

### Task 10: 同步全部收藏 + 空状态文案

**Files:**
- Create: `nga_phone_base_3.0/src/main/java/sp/phone/task/FavoriteSyncTask.java`
- Modify: `nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicFavoriteFragment.java`

**Interfaces:**
- Consumes: Task 4 的 `upsertAll`、Task 3 的 `pruneTo`；既有的 `TopicCacheUpdateTask.setDownloadRunning(boolean)`；`TopicListModel.loadTopicList(int page, TopicListParam param, OnHttpCallBack<TopicListInfo> callBack)`
- Produces: `static void FavoriteSyncTask.execute(Runnable onFinished)`

已确认的既有事实，直接用不必再查：分页方法叫 **`loadTopicList`**（不是 `loadPage`）；空状态的 `TextView` id 是 **`R.id.tv_empty`**，`list_empty_view.xml` 里已经有了，**不需要改布局**。

- [ ] **Step 1: 写同步任务**

```java
package sp.phone.task;

import android.os.Handler;
import android.os.Looper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import gov.anzong.androidnga.base.util.ToastUtils;
import gov.anzong.androidnga.common.util.NLog;
import gov.anzong.androidnga.favorite.FavoriteStore;
import gov.anzong.androidnga.http.OnHttpCallBack;
import sp.phone.mvp.model.TopicListModel;
import sp.phone.mvp.model.entity.ThreadPageInfo;
import sp.phone.mvp.model.entity.TopicListInfo;
import sp.phone.param.TopicListParam;
import sp.phone.rxjava.RxLifecycleProvider;

/**
 * 把整个收藏夹分页拉一遍，补全本地快照。
 *
 * 节奏和 TopicCacheAllTask 一致：500ms 页间隔 + 失败退避 + 连续失败中止。
 * 收藏夹页数多时不限速会触发 NGA 限流，而限流的表现是连续 302 重定向，
 * OkHttp 跟到第 21 跳才抛 ProtocolException——失败一页等于打 21 次请求。
 */
public class FavoriteSyncTask {

    private static final String TAG = "FavoriteSyncTask";

    private static final long PAGE_INTERVAL_MS = 500;

    private static final long[] BACKOFF_MS = {2000, 4000, 8000};

    private static boolean sRunning;

    private final TopicListModel mModel = new TopicListModel();

    private final RxLifecycleProvider mLifecycleProvider = new RxLifecycleProvider();

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    /** 本轮见到的全部 tid，只有完整成功才拿它去 prune */
    private final Set<Integer> mSeenTids = new HashSet<>();

    private TopicListParam mParam;

    private Runnable mOnFinished;

    private int mPage = 1;

    private int mRetryCount;

    public static void execute(Runnable onFinished) {
        if (sRunning) {
            ToastUtils.info("正在同步中…");
            return;
        }
        sRunning = true;
        // 同步期间让帖子下载和限速更新停手，两条请求流叠加同样会触发限流
        TopicCacheUpdateTask.setDownloadRunning(true);
        FavoriteSyncTask task = new FavoriteSyncTask();
        task.mOnFinished = onFinished;
        task.start();
    }

    private void start() {
        mParam = new TopicListParam();
        mParam.favor = 1;
        mModel.setLifecycleProvider(mLifecycleProvider);
        ToastUtils.info("开始同步全部收藏…");
        loadCurrentPage();
    }

    private void loadCurrentPage() {
        mModel.loadTopicList(mPage, mParam, new OnHttpCallBack<TopicListInfo>() {
            @Override
            public void onSuccess(TopicListInfo data) {
                List<ThreadPageInfo> pageList = data == null ? null : data.getThreadPageList();
                if (pageList == null || pageList.isEmpty()) {
                    finishSuccessfully();
                    return;
                }
                FavoriteStore.getInstance().upsertAll(pageList);
                for (ThreadPageInfo info : pageList) {
                    mSeenTids.add(info.getTid());
                }
                mRetryCount = 0;
                mPage++;
                mHandler.postDelayed(FavoriteSyncTask.this::loadCurrentPage, PAGE_INTERVAL_MS);
            }

            @Override
            public void onError(String text) {
                onPageFailed(text);
            }

            @Override
            public void onError(String msg, Throwable t) {
                NLog.e(TAG, "sync page " + mPage + " error: " + t);
                onPageFailed(msg);
            }
        });
    }

    private void onPageFailed(String error) {
        NLog.e(TAG, "sync page " + mPage + " failed(" + (mRetryCount + 1) + "): " + error);
        if (mRetryCount >= BACKOFF_MS.length) {
            // **中途失败绝不 prune**：没拉到的收藏会被当成已取消而删掉
            ToastUtils.error("同步中断在第" + mPage + "页，已补全一部分，稍后再试");
            finish();
            return;
        }
        long delay = BACKOFF_MS[mRetryCount];
        mRetryCount++;
        mHandler.postDelayed(this::loadCurrentPage, delay);
    }

    /** 只有完整拉完才按服务端全集清理本地孤儿条目 */
    private void finishSuccessfully() {
        FavoriteStore.getInstance().snapshot().pruneTo(mSeenTids);
        FavoriteStore.getInstance().save();
        ToastUtils.success("同步完成，共" + mSeenTids.size() + "条收藏");
        finish();
    }

    private void finish() {
        sRunning = false;
        TopicCacheUpdateTask.setDownloadRunning(false);
        if (mOnFinished != null) {
            mOnFinished.run();
        }
    }
}
```

- [ ] **Step 2: 菜单接上，并补空状态文案**

在 `TopicFavoriteFragment` 加：

```java
    @Override
    public boolean onOptionsItemSelected(@NonNull android.view.MenuItem item) {
        if (item.getItemId() == R.id.menu_sync_all_favorite) {
            sp.phone.task.FavoriteSyncTask.execute(() -> {
                renderFolderStrip();
                reloadCurrentView();
            });
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** 空列表要说清楚是哪一种空，否则看起来像收藏丢了 */
    private void updateEmptyText() {
        View root = getView();
        TextView emptyText = root == null ? null : root.findViewById(R.id.tv_empty);
        if (emptyText == null) {
            return;
        }
        if (!mSearchKeyword.isEmpty()) {
            emptyText.setText("没有匹配的收藏");
        } else if (!mCurrentFolder.isEmpty()) {
            emptyText.setText("这个文件夹还没有帖子");
        } else if (!FavoriteStore.getInstance().snapshot().folders.isEmpty()) {
            emptyText.setText("所有收藏都已分类，点上方文件夹查看");
        } else {
            emptyText.setText("还没有收藏任何帖子");
        }
    }
```

在 `reloadCurrentView()` 和 `renderSearchResult()` 的**末尾各加一行** `updateEmptyText();`。

- [ ] **Step 3: 编译并装机**

```bash
export JAVA_HOME="D:/Program Files/Android/Android Studio/jbr" && ./gradlew.bat :nga_phone_base_3.0:assembleDebug --console=plain -q 2>&1 | grep -E "^e: |error:" | head
```

```bash
C:/Users/codea/AppData/Local/Android/Sdk/platform-tools/adb.exe -s 374d7eee install -r nga_phone_base_3.0/build/outputs/apk/debug/nga_phone_base_3.0-debug.apk
```

- [ ] **Step 4: 手测**

1. 溢出菜单 →「同步全部收藏」→ 提示开始，结束后提示条数
2. 看日志确认页与页之间约 500ms：

```bash
C:/Users/codea/AppData/Local/Android/Sdk/platform-tools/adb.exe -s 374d7eee logcat -d | grep FavoriteSyncTask
```

3. 同步后搜一个**之前从没往下翻到过**的老收藏 → 能搜到（验证快照补全）
4. 把根列表的帖子全部归类 → 显示「所有收藏都已分类，点上方文件夹查看」
5. 进空文件夹 → 显示「这个文件夹还没有帖子」
6. 搜一个不存在的词 → 显示「没有匹配的收藏」

- [ ] **Step 5: 提交**

```bash
git add nga_phone_base_3.0/src/main/java/sp/phone/task/FavoriteSyncTask.java nga_phone_base_3.0/src/main/java/sp/phone/ui/fragment/TopicFavoriteFragment.java
git commit -m "feat: 同步全部收藏与空状态文案"
```

---

## 完成后的整体验证

```bash
export JAVA_HOME="D:/Program Files/Android/Android Studio/jbr" && ./gradlew.bat :nga_phone_base_3.0:testDebugUnitTest --console=plain
```

Expected: 全部单测通过，含既有的 `CacheUpdateQueueTest`、`PriceGapTest`、`FilterWordModelTest`。

**最关键的一条端到端回归**：建一个文件夹，归几个帖子进去，**下拉刷新收藏夹**，确认分类仍在。这条覆盖 spec 里标出的「upsert 不能覆盖 folder」——写错了不会报错，只会静默清空用户的全部分类。
