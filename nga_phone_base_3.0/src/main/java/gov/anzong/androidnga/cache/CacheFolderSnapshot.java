package gov.anzong.androidnga.cache;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 缓存帖子的本地分类，内存模型与全部业务逻辑。
 *
 * 和收藏夹的快照不同，这里**只存归类关系**：标题、作者这些字段在
 * {@code filesDir/cache/<tid>/<tid>.json} 里已经有一份，缓存列表每次都是从磁盘
 * 全量读出来的，再存一遍只会两份数据打架。
 *
 * 刻意不依赖任何 Android API，好让规则能直接单测。磁盘读写在 CacheFolderStore。
 */
public class CacheFolderSnapshot {

    /** 文件夹名，有序；允许空文件夹（可以先建夹再往里放帖子） */
    public List<String> folders = new ArrayList<>();

    /**
     * tid 字符串 -> 文件夹名。
     *
     * **未分类的帖子不在这里出现**，而不是存一条空串——缓存动辄上百个帖子，
     * 给每个都落一条空记录纯属让文件白白变大。
     */
    public Map<String, String> assignments = new LinkedHashMap<>();

    /** 设置某个帖子的文件夹，空串表示移出文件夹 */
    public void setFolder(int tid, String folder) {
        String key = String.valueOf(tid);
        String value = folder == null ? "" : folder;
        if (value.isEmpty()) {
            assignments.remove(key);
        } else {
            assignments.put(key, value);
        }
    }

    /** 没归过类的 tid 返回空串（视为未分类），不抛异常 */
    public String folderOf(int tid) {
        String folder = assignments.get(String.valueOf(tid));
        return folder == null ? "" : folder;
    }

    /** 新建文件夹。空名、纯空格、重名一律拒绝并返回 false，由调用方提示用户 */
    public boolean createFolder(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || folders.contains(trimmed)) {
            return false;
        }
        folders.add(trimmed);
        return true;
    }

    /**
     * 重命名文件夹，并把夹内所有条目一并改掉——
     * 漏了这一步就会留下一批指向旧名、哪个夹都进不去的孤儿。
     */
    public boolean renameFolder(String oldName, String newName) {
        String trimmed = newName == null ? "" : newName.trim();
        int index = folders.indexOf(oldName);
        if (index < 0 || trimmed.isEmpty() || folders.contains(trimmed)) {
            return false;
        }
        folders.set(index, trimmed);
        for (Map.Entry<String, String> entry : assignments.entrySet()) {
            if (oldName.equals(entry.getValue())) {
                entry.setValue(trimmed);
            }
        }
        return true;
    }

    /**
     * 删除文件夹。**缓存不删**，只是退回未分类——删夹是整理动作，
     * 用户不会期望连带丢掉辛苦缓存下来的帖子。
     */
    public void deleteFolder(String name) {
        if (!folders.remove(name)) {
            return;
        }
        assignments.values().removeIf(name::equals);
    }

    /** 某个帖子的缓存被删掉时，顺手清掉它的归类 */
    public void forget(int tid) {
        assignments.remove(String.valueOf(tid));
    }

    /**
     * 按磁盘上现存的 tid 清理归类记录。
     *
     * 缓存列表每次都是把 cache 目录全量读一遍，所以这里的全集是可信的——
     * 这点和收藏夹相反，收藏夹只加载了前几页，照那个清理会误删。
     * 全集为空时跳过，那多半是读盘出错而不是真的一个缓存都没有。
     */
    public void pruneTo(Set<Integer> existingTids) {
        if (existingTids == null || existingTids.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<String, String>> it = assignments.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> entry = it.next();
            try {
                if (!existingTids.contains(Integer.valueOf(entry.getKey()))) {
                    it.remove();
                }
            } catch (NumberFormatException e) {
                it.remove();
            }
        }
    }
}
