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
