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
