package gov.anzong.androidnga.folder;

import java.util.List;

/**
 * 帖子列表的本地文件夹分类。
 *
 * 收藏夹和缓存各有一份独立实现（{@code FavoriteStore} / {@code CacheFolderStore}），
 * 互不影响——两个列表本来就不是同一批帖子，共用一套夹只会让人分不清哪个是哪个。
 *
 * 界面那一层（TopicFolderFragment）只认这个接口，不关心背后存在哪。
 */
public interface FolderRepository {

    /** 文件夹名，有序 */
    List<String> folders();

    /** 没归过类的 tid 返回空串，不抛异常 */
    String folderOf(int tid);

    /** 空串表示移出文件夹 */
    void setFolder(int tid, String folder);

    /** 空名、纯空格、重名一律返回 false，由调用方提示用户 */
    boolean createFolder(String name);

    /** 连同夹内条目一起改名；重名、空名、夹不存在返回 false */
    boolean renameFolder(String oldName, String newName);

    /** 只删夹，里面的帖子退回未分类 */
    void deleteFolder(String name);

    /** 落盘。失败只降级，不该打断用户浏览 */
    void save();
}
