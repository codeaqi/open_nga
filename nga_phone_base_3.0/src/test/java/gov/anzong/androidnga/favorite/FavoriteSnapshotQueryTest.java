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
