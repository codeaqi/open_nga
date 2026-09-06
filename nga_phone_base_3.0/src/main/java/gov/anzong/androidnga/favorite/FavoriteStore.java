package gov.anzong.androidnga.favorite;

import com.alibaba.fastjson.JSON;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import gov.anzong.androidnga.base.util.ContextUtils;
import gov.anzong.androidnga.base.util.ThreadUtils;
import gov.anzong.androidnga.folder.FolderRepository;
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
public class FavoriteStore implements FolderRepository {

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
    @Override
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
     * 指向已不存在文件夹的条目退回未分类，避免留下点不进去的孤儿；
     * 键不是合法数字的条目直接丢弃——parse 之外的代码（如 pruneTo）都假定
     * items 的键能用 Integer.valueOf 解析，手改或损坏的文件不能破坏这个假设。
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
        Iterator<Map.Entry<String, FavoriteItem>> it = parsed.items.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, FavoriteItem> entry = it.next();
            try {
                Integer.valueOf(entry.getKey());
            } catch (NumberFormatException e) {
                it.remove();
                continue;
            }
            FavoriteItem item = entry.getValue();
            if (item == null) {
                it.remove();
                continue;
            }
            if (item.folder == null || !parsed.folders.contains(item.folder)) {
                item.folder = "";
            }
        }
        return parsed;
    }

    // ==================== FolderRepository ====================

    @Override
    public List<String> folders() {
        return mSnapshot.folders;
    }

    @Override
    public String folderOf(int tid) {
        return mSnapshot.folderOf(tid);
    }

    @Override
    public void setFolder(int tid, String folder) {
        mSnapshot.setFolder(tid, folder);
    }

    @Override
    public boolean createFolder(String name) {
        return mSnapshot.createFolder(name);
    }

    @Override
    public boolean renameFolder(String oldName, String newName) {
        return mSnapshot.renameFolder(oldName, newName);
    }

    @Override
    public void deleteFolder(String name) {
        mSnapshot.deleteFolder(name);
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
