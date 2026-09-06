package gov.anzong.androidnga.cache;

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

/**
 * 缓存帖子分类的持久化。
 *
 * 和收藏夹的快照一样，这是**增强而非主数据源**：文件损坏、写入失败都只降级成
 * "全部未分类"，缓存列表本身照常能看。
 *
 * 单例常驻内存，写盘走子线程。
 */
public class CacheFolderStore implements FolderRepository {

    private static final String TAG = "CacheFolderStore";

    private static final String FILE_NAME = "cache_folders.json";

    private static final CacheFolderStore sInstance = new CacheFolderStore();

    private CacheFolderSnapshot mSnapshot = new CacheFolderSnapshot();

    private boolean mLoaded;

    private CacheFolderStore() {
    }

    public static CacheFolderStore getInstance() {
        return sInstance;
    }

    public CacheFolderSnapshot snapshot() {
        return mSnapshot;
    }

    private static File file() {
        return new File(ContextUtils.getContext().getFilesDir(), FILE_NAME);
    }

    /** 读盘。进入缓存列表时调一次，之后都用内存里的 */
    public void load() {
        if (mLoaded) {
            return;
        }
        mLoaded = true;
        try {
            File f = file();
            mSnapshot = f.exists()
                    ? parse(FileUtils.readFileToString(f)) : new CacheFolderSnapshot();
        } catch (Exception e) {
            System.err.println(TAG + " load failed, start empty: " + e);
            mSnapshot = new CacheFolderSnapshot();
        }
    }

    /** 写盘。失败只记日志——丢一次分类不该打断用户看缓存 */
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
     * 解析分类文件。做成静态纯函数是为了让容错逻辑能脱离 Android 单测，
     * 所以这里用 System.err 而不是 NLog——NLog 依赖 Android，单测里跑不起来。
     *
     * 任何解析失败都返回空库而不是抛异常；缺字段补默认值；
     * 指向已不存在文件夹的归类退回未分类；键不是合法数字的条目直接丢弃——
     * pruneTo 等代码都假定键能用 Integer.valueOf 解析。
     */
    public static CacheFolderSnapshot parse(String json) {
        CacheFolderSnapshot parsed = null;
        if (json != null && !json.trim().isEmpty()) {
            try {
                parsed = JSON.parseObject(json, CacheFolderSnapshot.class);
            } catch (Exception e) {
                System.err.println(TAG + " parse failed, start empty: " + e);
            }
        }
        if (parsed == null) {
            return new CacheFolderSnapshot();
        }
        if (parsed.folders == null) {
            parsed.folders = new ArrayList<>();
        }
        if (parsed.assignments == null) {
            parsed.assignments = new LinkedHashMap<>();
        }
        Iterator<Map.Entry<String, String>> it = parsed.assignments.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> entry = it.next();
            try {
                Integer.valueOf(entry.getKey());
            } catch (NumberFormatException e) {
                it.remove();
                continue;
            }
            if (entry.getValue() == null || !parsed.folders.contains(entry.getValue())) {
                it.remove();
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
}
