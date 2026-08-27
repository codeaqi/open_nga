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
