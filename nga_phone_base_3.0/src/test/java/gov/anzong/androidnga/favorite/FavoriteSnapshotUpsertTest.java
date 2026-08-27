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
