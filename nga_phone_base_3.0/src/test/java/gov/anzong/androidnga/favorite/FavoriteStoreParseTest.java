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

    /** 手改或损坏的文件可能塞进非数字键，parse 要丢掉它，
     *  否则 pruneTo 解析键时会抛 NumberFormatException */
    @Test
    public void parseDropsEntriesWithNonNumericKey() {
        String json = "{\"items\":{\"abc\":{\"tid\":0,\"subject\":\"坏键\"},"
                + "\"7\":{\"tid\":7,\"subject\":\"好键\"}}}";
        FavoriteSnapshot s = FavoriteStore.parse(json);
        assertEquals(1, s.items.size());
        assertEquals("好键", s.items.get("7").subject);
    }
}
