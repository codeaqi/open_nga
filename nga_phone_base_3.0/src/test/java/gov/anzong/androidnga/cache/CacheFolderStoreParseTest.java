package gov.anzong.androidnga.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CacheFolderStoreParseTest {

    @Test
    public void parseRestoresFoldersAndAssignments() {
        String json = "{\"folders\":[\"待看\",\"技术\"],"
                + "\"assignments\":{\"1\":\"待看\",\"2\":\"技术\"}}";

        CacheFolderSnapshot s = CacheFolderStore.parse(json);

        assertEquals(2, s.folders.size());
        assertEquals("待看", s.folders.get(0));
        assertEquals("待看", s.folderOf(1));
        assertEquals("技术", s.folderOf(2));
    }

    /** 文件损坏当空库重建，绝不能崩——分类是增强，不是缓存本身 */
    @Test
    public void parseCorruptJsonReturnsEmptySnapshot() {
        CacheFolderSnapshot s = CacheFolderStore.parse("{这不是合法 json");
        assertNotNull(s);
        assertTrue(s.folders.isEmpty());
        assertTrue(s.assignments.isEmpty());
    }

    @Test
    public void parseEmptyOrNullReturnsEmptySnapshot() {
        assertTrue(CacheFolderStore.parse("").assignments.isEmpty());
        assertTrue(CacheFolderStore.parse(null).assignments.isEmpty());
    }

    /** 指向已删文件夹的归类要退回未分类，否则留下点不进去的孤儿 */
    @Test
    public void parseDropsAssignmentsToMissingFolder() {
        String json = "{\"folders\":[\"待看\"],\"assignments\":{\"1\":\"待看\",\"2\":\"没了的夹\"}}";

        CacheFolderSnapshot s = CacheFolderStore.parse(json);

        assertEquals("待看", s.folderOf(1));
        assertEquals("", s.folderOf(2));
    }

    /** 键不是数字的条目直接丢掉：pruneTo 之类的代码都假定键能转成 tid */
    @Test
    public void parseDropsNonNumericKeys() {
        String json = "{\"folders\":[\"待看\"],\"assignments\":{\"abc\":\"待看\",\"3\":\"待看\"}}";

        CacheFolderSnapshot s = CacheFolderStore.parse(json);

        assertEquals(1, s.assignments.size());
        assertEquals("待看", s.folderOf(3));
    }

    @Test
    public void parseFillsMissingFields() {
        CacheFolderSnapshot s = CacheFolderStore.parse("{}");
        assertNotNull(s.folders);
        assertNotNull(s.assignments);
    }
}
