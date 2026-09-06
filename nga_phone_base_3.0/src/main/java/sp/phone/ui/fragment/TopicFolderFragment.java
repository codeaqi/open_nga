package sp.phone.ui.fragment;

import android.app.AlertDialog;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;

import com.alibaba.fastjson.JSON;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import gov.anzong.androidnga.R;
import gov.anzong.androidnga.base.util.ToastUtils;
import gov.anzong.androidnga.folder.FolderRepository;
import sp.phone.mvp.model.entity.ThreadPageInfo;

/**
 * 带本地文件夹分类和本地搜索的帖子列表。
 *
 * 收藏夹和「我的缓存」共用这一套：界面按文件管理器的样子组织，顶栏一行文件夹，
 * 下面是当前视图（根视图＝未分类）的帖子；搜索跨文件夹，在本地做。
 *
 * 两边唯一的差别在数据从哪来，全部收在下面几个抽象方法里。分类本身是**各自
 * 独立的两套**（见 {@link FolderRepository}）。
 */
public abstract class TopicFolderFragment extends TopicSearchFragment
        implements View.OnLongClickListener {

    /** 当前所在文件夹，空串表示根视图（显示未分类的帖子） */
    protected String mCurrentFolder = "";

    /** 非空表示正在搜索，此时无视 mCurrentFolder，展示全部条目里的匹配项 */
    protected String mSearchKeyword = "";

    private LinearLayout mFolderStrip;

    private View mFolderScroll;

    // ==================== 子类要提供的东西 ====================

    /** 分类存哪：收藏夹和缓存各有一份，互不影响 */
    protected abstract FolderRepository folderStore();

    /** 「收藏」或「缓存」，用来拼各种提示文案 */
    protected abstract String itemNoun();

    /** 退回根视图时的标题 */
    protected abstract String rootTitle();

    /** 进入某个文件夹时的标题 */
    protected abstract String folderTitle(String folder);

    /** 长按菜单里删除项点下去之后真正做的事 */
    protected abstract void deleteItem(ThreadPageInfo info);

    /**
     * 搜索的候选全集。
     *
     * 收藏夹给的是本地快照（比当前加载的几页多），缓存给的是已经全量读出来的列表。
     */
    protected abstract List<ThreadPageInfo> searchCandidates();

    // ==================== 界面骨架 ====================

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_topic_folder_list, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mFolderStrip = view.findViewById(R.id.folder_strip);
        mFolderScroll = view.findViewById(R.id.folder_scroll);
        mAdapter.setOnLongClickListener(this);
    }

    /** 只保留当前视图该看到的那些帖子：根视图＝未分类，夹内＝该夹 */
    protected List<ThreadPageInfo> filterForCurrentView(List<ThreadPageInfo> pageList) {
        List<ThreadPageInfo> visible = new ArrayList<>();
        if (pageList == null) {
            return visible;
        }
        for (ThreadPageInfo info : pageList) {
            if (mCurrentFolder.equals(folderStore().folderOf(info.getTid()))) {
                visible.add(info);
            }
        }
        return visible;
    }

    // ==================== 文件夹条 ====================

    /** 文件夹区：一个都没建时整块隐藏，不白占一条高度 */
    protected void renderFolderStrip() {
        List<String> folders = folderStore().folders();
        mFolderStrip.removeAllViews();

        if (!mCurrentFolder.isEmpty()) {
            // 夹内：整条换成返回入口
            TextView back = new TextView(getContext());
            back.setText("← " + mCurrentFolder);
            back.setTextSize(14);
            back.setPadding(dp(12), dp(6), dp(12), dp(6));
            back.setTextColor(android.graphics.Color.WHITE);
            back.setOnClickListener(v -> backToRoot());
            mFolderScroll.setVisibility(View.VISIBLE);
            mFolderStrip.addView(back);
            return;
        }

        if (folders.isEmpty()) {
            mFolderScroll.setVisibility(View.GONE);
            return;
        }
        mFolderScroll.setVisibility(View.VISIBLE);
        for (String folder : folders) {
            mFolderStrip.addView(createFolderChip(folder));
        }
    }

    protected int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /**
     * 文件夹图标用矢量图而不是 📁 —— 彩色 emoji 在系统字体里是斜着的黄块，
     * 跟顶栏的白字绿底格格不入。尺寸按 getTextSize 走，字体缩放调大时图标一起大。
     */
    private void setFolderIcon(TextView chip) {
        Drawable icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_folder_24);
        if (icon == null) {
            return;
        }
        icon = icon.mutate();
        int size = Math.round(chip.getTextSize());
        icon.setBounds(0, 0, size, size);
        icon.setTint(android.graphics.Color.WHITE);
        chip.setCompoundDrawablesRelative(icon, null, null, null);
        chip.setCompoundDrawablePadding(dp(5));
    }

    private TextView createFolderChip(final String folder) {
        TextView chip = new TextView(getContext());
        chip.setText(folder);
        chip.setTextSize(14);
        chip.setPadding(dp(12), dp(6), dp(12), dp(6));
        chip.setTextColor(android.graphics.Color.WHITE);
        setFolderIcon(chip);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(6);
        chip.setLayoutParams(lp);
        chip.setOnClickListener(v -> enterFolder(folder));
        chip.setOnLongClickListener(v -> {
            showFolderMenu(folder);
            return true;
        });
        return chip;
    }

    /**
     * 立刻改标题栏。
     *
     * 父类的 setTitle 只把字符串存进字段，真正写到 ActionBar 是在 onResume 里做的；
     * 进出文件夹发生在已经 resume 之后，光调 setTitle 界面上不会有任何变化。
     */
    private void applyTitle(String title) {
        setTitle(title);
        if (getActivity() != null) {
            getActivity().setTitle(title);
        }
    }

    private void enterFolder(String folder) {
        mCurrentFolder = folder;
        applyTitle(folderTitle(folder));
        renderFolderStrip();
        reloadCurrentView();
    }

    private void backToRoot() {
        mCurrentFolder = "";
        applyTitle(rootTitle());
        renderFolderStrip();
        reloadCurrentView();
    }

    /** 供宿主 Activity 的返回键调用：在文件夹里就先退回根视图，返回 true 表示已消费 */
    public boolean onBackPressedHandled() {
        if (mCurrentFolder.isEmpty()) {
            return false;
        }
        backToRoot();
        return true;
    }

    /**
     * 切换视图时重新过滤**已经加载到的**那些帖子，不重新请求——
     * 切个文件夹就重拉一遍列表，正是会触发限流的那种行为。
     */
    protected void reloadCurrentView() {
        mAdapter.setData(null);
        if (mTopicListInfo != null) {
            mAdapter.setData(filterForCurrentView(mTopicListInfo.getThreadPageList()));
        }
        updateEmptyText();
    }

    // ==================== 帖子的归类与删除 ====================

    /**
     * 长按出菜单而不是直接弹删除确认。
     *
     * 归类和删除都挂在长按上，而且直接删除太容易误触。
     */
    @Override
    public boolean onLongClick(final View view) {
        final ThreadPageInfo info = (ThreadPageInfo) view.getTag();
        new AlertDialog.Builder(getContext())
                .setTitle(info.getSubject())
                .setItems(new CharSequence[]{"移到文件夹", "删除" + itemNoun()}, (dialog, which) -> {
                    if (which == 0) {
                        showMoveDialog(info);
                    } else {
                        confirmRemove(info);
                    }
                })
                .show();
        return true;
    }

    private void confirmRemove(final ThreadPageInfo info) {
        new AlertDialog.Builder(getContext())
                .setMessage(getString(R.string.delete_favo_confirm_text))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> deleteItem(info))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** 单选：现有文件夹 + 新建 + 移出。一个帖子只属于一个文件夹，所以是单选 */
    private void showMoveDialog(final ThreadPageInfo info) {
        final List<String> folders = new ArrayList<>(folderStore().folders());
        final CharSequence[] options = new CharSequence[folders.size() + 2];
        for (int i = 0; i < folders.size(); i++) {
            options[i] = folders.get(i);
        }
        options[folders.size()] = "新建文件夹…";
        options[folders.size() + 1] = "移出文件夹";

        new AlertDialog.Builder(getContext())
                .setTitle("移到文件夹")
                .setItems(options, (dialog, which) -> {
                    if (which == folders.size()) {
                        showCreateFolderDialog(info);
                    } else if (which == folders.size() + 1) {
                        moveTo(info, "");
                    } else {
                        moveTo(info, folders.get(which));
                    }
                })
                .show();
    }

    private void showCreateFolderDialog(final ThreadPageInfo info) {
        final EditText input = new EditText(getContext());
        input.setHint("文件夹名，如 红利");
        new AlertDialog.Builder(getContext())
                .setTitle("新建文件夹")
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!folderStore().createFolder(name)) {
                        ToastUtils.error(name.isEmpty() ? "文件夹名不能为空" : "已存在同名文件夹");
                        return;
                    }
                    moveTo(info, name);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * 归类后立刻把该帖从当前列表移除——它已经不属于这个视图了，
     * 留在原地会让人以为没生效。
     */
    private void moveTo(ThreadPageInfo info, String folder) {
        folderStore().setFolder(info.getTid(), folder);
        folderStore().save();
        renderFolderStrip();
        mAdapter.removeItem(info);
        ToastUtils.success(folder.isEmpty() ? "已移出文件夹" : "已移到「" + folder + "」");
    }

    // ==================== 文件夹的重命名与删除 ====================

    private void showFolderMenu(final String folder) {
        new AlertDialog.Builder(getContext())
                .setTitle(folder)
                .setItems(new CharSequence[]{"重命名", "删除文件夹"}, (dialog, which) -> {
                    if (which == 0) {
                        showRenameFolderDialog(folder);
                    } else {
                        confirmDeleteFolder(folder);
                    }
                })
                .show();
    }

    private void showRenameFolderDialog(final String folder) {
        final EditText input = new EditText(getContext());
        input.setText(folder);
        new AlertDialog.Builder(getContext())
                .setTitle("重命名文件夹")
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    // 输入框预填的就是当前名字，不改直接点确定是最常见的操作。
                    // 这种情况要当成功的空操作，否则 renameFolder 的重名检查会撞上它自己，
                    // 用户会看到莫名其妙的「已存在同名文件夹」
                    if (name.equals(folder)) {
                        return;
                    }
                    if (!folderStore().renameFolder(folder, name)) {
                        ToastUtils.error(name.isEmpty() ? "文件夹名不能为空" : "已存在同名文件夹");
                        return;
                    }
                    folderStore().save();
                    if (mCurrentFolder.equals(folder)) {
                        mCurrentFolder = name;
                        applyTitle(folderTitle(name));
                    }
                    renderFolderStrip();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * 删夹只是把里面的帖子放回未分类，对话框必须写清楚这点，
     * 否则用户会以为连帖子一起删了而不敢用。
     */
    private void confirmDeleteFolder(final String folder) {
        new AlertDialog.Builder(getContext())
                .setTitle("删除文件夹「" + folder + "」")
                .setMessage("里面的帖子会回到未分类，" + itemNoun() + "本身不会被删除。")
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    folderStore().deleteFolder(folder);
                    folderStore().save();
                    if (mCurrentFolder.equals(folder)) {
                        backToRoot();
                    } else {
                        renderFolderStrip();
                        reloadCurrentView();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ==================== 搜索 ====================

    /** 子类 inflate 完自己的菜单后把搜索项交给这里挂监听 */
    protected void bindSearchView(Menu menu, int searchItemId) {
        MenuItem item = menu.findItem(searchItemId);
        if (item == null || !(item.getActionView() instanceof SearchView)) {
            return;
        }
        SearchView searchView = (SearchView) item.getActionView();
        searchView.setQueryHint("搜索" + itemNoun() + "的帖子");
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                renderSearchResult(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                renderSearchResult(newText);
                return true;
            }
        });
    }

    /**
     * 搜索**跨文件夹**：搜的时候通常不记得当初把帖子放哪了。
     *
     * 搜索期间隐藏文件夹区——结果是跨文件夹的平铺列表，此时留着文件夹区
     * 会让人误以为搜索被限定在某个夹里。清空关键词后恢复到搜索前的视图。
     */
    private void renderSearchResult(String keyword) {
        mSearchKeyword = keyword == null ? "" : keyword.trim();
        if (mSearchKeyword.isEmpty()) {
            renderFolderStrip();
            reloadCurrentView();
            return;
        }
        mFolderScroll.setVisibility(View.GONE);

        String key = mSearchKeyword.toLowerCase(Locale.ROOT);
        List<ThreadPageInfo> rows = new ArrayList<>();
        for (ThreadPageInfo candidate : searchCandidates()) {
            if (candidate == null || !matches(candidate, key)) {
                continue;
            }
            rows.add(asSearchRow(candidate));
        }
        mAdapter.setData(null);
        mAdapter.setData(rows);
        updateEmptyText();
    }

    /** 标题或作者命中即可，不区分大小写 */
    private boolean matches(ThreadPageInfo info, String lowerKeyword) {
        String subject = info.getSubject() == null
                ? "" : info.getSubject().toLowerCase(Locale.ROOT);
        String author = info.getAuthor() == null
                ? "" : info.getAuthor().toLowerCase(Locale.ROOT);
        return subject.contains(lowerKeyword) || author.contains(lowerKeyword);
    }

    /**
     * 搜索结果给的是**副本**，不是列表里那些对象本身。
     *
     * 下面要往标题后面缀文件夹名，直接改原对象会把内存里（缓存列表还有磁盘上）
     * 的真实标题污染掉。整体序列化一遍是最省心的深拷贝，字段以后加了也不会漏。
     */
    private ThreadPageInfo asSearchRow(ThreadPageInfo src) {
        ThreadPageInfo row = JSON.parseObject(JSON.toJSONString(src), ThreadPageInfo.class);
        if (row == null) {
            return src;
        }
        // 已分类的在标题后缀出所属文件夹，省得还要点进去才知道放哪了
        String folder = folderStore().folderOf(src.getTid());
        if (!folder.isEmpty()) {
            row.setSubject(src.getSubject() + "  [" + folder + "]");
        }
        return row;
    }

    /** 空列表要说清楚是哪一种空，否则看起来像数据丢了 */
    protected void updateEmptyText() {
        View root = getView();
        TextView emptyText = root == null ? null : root.findViewById(R.id.tv_empty);
        if (emptyText == null) {
            return;
        }
        if (!mSearchKeyword.isEmpty()) {
            emptyText.setText("没有匹配的" + itemNoun());
        } else if (!mCurrentFolder.isEmpty()) {
            emptyText.setText("这个文件夹还没有帖子");
        } else if (!folderStore().folders().isEmpty()) {
            emptyText.setText("所有" + itemNoun() + "都已分类，点上方文件夹查看");
        } else {
            emptyText.setText("还没有" + itemNoun() + "任何帖子");
        }
    }
}
