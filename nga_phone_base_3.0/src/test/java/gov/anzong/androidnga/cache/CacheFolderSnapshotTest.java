package gov.anzong.androidnga.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public class CacheFolderSnapshotTest {

    @Test
    public void createFolderAppendsInOrder() {
        CacheFolderSnapshot s = new CacheFolderSnapshot();
        assertTrue(s.createFolder("待看"));
        assertTrue(s.createFolder("技术"));
        assertEquals(Arrays.asList("待看", "技术"), s.folders);
    }

    @Test
    public void createFolderRejectsDuplicateOrBlankName() {
        CacheFolderSnapshot s = new CacheFolderSnapshot();
        assertTrue(s.createFolder("待看"));
        assertFalse("重名要被拒", s.createFolder("待看"));
        assertFalse(s.createFolder(""));
        assertFalse(s.createFolder("   "));
        assertFalse(s.createFolder(null));
        assertEquals(1, s.folders.size());
    }

    @Test
    public void createFolderTrimsName() {
        CacheFolderSnapshot s = new CacheFolderSnapshot();
        assertTrue(s.createFolder("  待看  "));
        assertEquals(Collections.singletonList("待看"), s.folders);
    }

    @Test
    public void unknownTidIsUnfiled() {
        assertEquals("", new CacheFolderSnapshot().folderOf(42));
    }

    /** 未分类不落一条空记录，否则文件会随缓存数量白白膨胀 */
    @Test
    public void movingOutOfFolderDropsTheEntry() {
        CacheFolderSnapshot s = new CacheFolderSnapshot();
        s.createFolder("待看");
        s.setFolder(1, "待看");
        assertEquals(1, s.assignments.size());

        s.setFolder(1, "");

        assertEquals("", s.folderOf(1));
        assertTrue(s.assignments.isEmpty());
    }

    /** 重命名要把夹内条目一并改掉，否则留下指向旧名、点不进去的孤儿 */
    @Test
    public void renameFolderRetagsAllEntries() {
        CacheFolderSnapshot s = new CacheFolderSnapshot();
        s.createFolder("待看");
        s.setFolder(1, "待看");
        s.setFolder(2, "待看");

        assertTrue(s.renameFolder("待看", "慢慢看"));

        assertEquals(Collections.singletonList("慢慢看"), s.folders);
        assertEquals("慢慢看", s.folderOf(1));
        assertEquals("慢慢看", s.folderOf(2));
    }

    @Test
    public void renameFolderRejectsDuplicateBlankOrUnknown() {
        CacheFolderSnapshot s = new CacheFolderSnapshot();
        s.createFolder("待看");
        s.createFolder("技术");

        assertFalse(s.renameFolder("待看", "技术"));
        assertFalse(s.renameFolder("待看", "  "));
        assertFalse(s.renameFolder("不存在", "新名"));
        assertEquals(Arrays.asList("待看", "技术"), s.folders);
    }

    /** 删夹是整理动作：缓存本身不动，只是退回未分类 */
    @Test
    public void deleteFolderMovesEntriesBackToUnfiled() {
        CacheFolderSnapshot s = new CacheFolderSnapshot();
        s.createFolder("待看");
        s.createFolder("技术");
        s.setFolder(1, "待看");
        s.setFolder(2, "技术");

        s.deleteFolder("待看");

        assertEquals(Collections.singletonList("技术"), s.folders);
        assertEquals("", s.folderOf(1));
        assertEquals("技术", s.folderOf(2));
    }

    @Test
    public void deleteUnknownFolderIsNoOp() {
        CacheFolderSnapshot s = new CacheFolderSnapshot();
        s.createFolder("待看");
        s.deleteFolder("不存在");
        assertEquals(Collections.singletonList("待看"), s.folders);
    }

    @Test
    public void forgetDropsOneEntryOnly() {
        CacheFolderSnapshot s = new CacheFolderSnapshot();
        s.createFolder("待看");
        s.setFolder(1, "待看");
        s.setFolder(2, "待看");

        s.forget(1);

        assertEquals("", s.folderOf(1));
        assertEquals("待看", s.folderOf(2));
    }

    /**
     * 缓存列表每次都是从磁盘全量读出来的，所以可以按它清理归类记录——
     * 这点和收藏夹不同，收藏夹只加载了前几页，照那个清理会误删。
     */
    @Test
    public void pruneDropsEntriesWhoseCacheIsGone() {
        CacheFolderSnapshot s = new CacheFolderSnapshot();
        s.createFolder("待看");
        s.setFolder(1, "待看");
        s.setFolder(2, "待看");

        s.pruneTo(new HashSet<>(Collections.singletonList(2)));

        assertEquals("", s.folderOf(1));
        assertEquals("待看", s.folderOf(2));
        assertEquals("文件夹本身不受影响", 1, s.folders.size());
    }

    /** 空集多半是读盘出错而不是真没缓存，宁可不清理 */
    @Test
    public void pruneWithEmptySetKeepsEverything() {
        CacheFolderSnapshot s = new CacheFolderSnapshot();
        s.createFolder("待看");
        s.setFolder(1, "待看");

        s.pruneTo(new HashSet<>());
        s.pruneTo(null);

        assertEquals("待看", s.folderOf(1));
    }
}
