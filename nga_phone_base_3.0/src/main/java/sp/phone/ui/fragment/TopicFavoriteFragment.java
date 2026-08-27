package sp.phone.ui.fragment;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;

import java.util.ArrayList;
import java.util.List;

import gov.anzong.androidnga.R;
import gov.anzong.androidnga.base.util.ToastUtils;
import gov.anzong.androidnga.favorite.FavoriteItem;
import gov.anzong.androidnga.favorite.FavoriteStore;
import sp.phone.task.FavoriteSyncTask;
import sp.phone.mvp.model.entity.ThreadPageInfo;
import sp.phone.mvp.model.entity.TopicListInfo;

/**
 * 收藏夹。
 *
 * 服务端只给一个平铺的收藏列表，文件夹是本地的（见 FavoriteStore）。
 * 界面按文件管理器的样子组织：上面一行文件夹，下面直接是未分类的帖子。
 */
public class TopicFavoriteFragment extends TopicSearchFragment implements View.OnLongClickListener {

    /** 当前所在文件夹，空串表示根视图（显示未分类的帖子） */
    private String mCurrentFolder = "";

    private LinearLayout mFolderStrip;

    private View mFolderScroll;

    /** 非空表示正在搜索，此时无视 mCurrentFolder，展示全部收藏里的匹配项 */
    private String mSearchKeyword = "";

    @Override
    protected void setTitle() {
        setTitle(R.string.bookmark_title);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_topic_favorite, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mFolderStrip = view.findViewById(R.id.folder_strip);
        mFolderScroll = view.findViewById(R.id.folder_scroll);
        FavoriteStore.getInstance().load();
        mAdapter.setOnLongClickListener(this);
        mPresenter.getRemovedTopic().observe(this, this::removeTopic);
        renderFolderStrip();
    }

    /**
     * 服务端每来一页收藏就写进本地快照，然后**只把当前视图该看到的那部分交给适配器**。
     *
     * 父类是直接把整页塞给适配器的，这里必须拦下来：根视图只显示未分类的帖子，
     * 已经归了类的应该只在对应文件夹里出现。
     */
    @Override
    public void setData(TopicListInfo result) {
        mTopicListInfo = result;
        List<ThreadPageInfo> pageList = result.getThreadPageList();
        FavoriteStore.getInstance().upsertAll(pageList);
        mAdapter.setData(filterForCurrentView(pageList));
    }

    private List<ThreadPageInfo> filterForCurrentView(List<ThreadPageInfo> pageList) {
        List<ThreadPageInfo> visible = new ArrayList<>();
        if (pageList == null) {
            return visible;
        }
        for (ThreadPageInfo info : pageList) {
            String folder = FavoriteStore.getInstance().snapshot().folderOf(info.getTid());
            if (mCurrentFolder.equals(folder)) {
                visible.add(info);
            }
        }
        return visible;
    }

    /** 文件夹区：一个都没建时整块隐藏，不白占一条高度 */
    private void renderFolderStrip() {
        List<String> folders = FavoriteStore.getInstance().snapshot().folders;
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView createFolderChip(final String folder) {
        TextView chip = new TextView(getContext());
        chip.setText("📁 " + folder);
        chip.setTextSize(14);
        chip.setPadding(dp(12), dp(6), dp(12), dp(6));
        chip.setTextColor(android.graphics.Color.WHITE);
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
        applyTitle("收藏夹 - " + folder);
        renderFolderStrip();
        reloadCurrentView();
    }

    private void backToRoot() {
        mCurrentFolder = "";
        applyTitle(getString(R.string.bookmark_title));
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
     * 切换视图时重新过滤**已经加载到的**那些帖子，不重新请求服务端——
     * 切个文件夹就重拉一遍收藏夹，正是会触发限流的那种行为。
     */
    private void reloadCurrentView() {
        mAdapter.setData(null);
        if (mTopicListInfo != null) {
            mAdapter.setData(filterForCurrentView(mTopicListInfo.getThreadPageList()));
        }
        updateEmptyText();
    }

    @Override
    public void removeTopic(int position) {
        mAdapter.removeItem(position);
    }

    @Override
    public void removeTopic(ThreadPageInfo pageInfo) {
        mAdapter.removeItem(pageInfo);
    }

    /**
     * 长按出菜单而不是直接弹删除确认。
     *
     * 归类和删除都挂在长按上，而且直接删除太容易误触——取消收藏是不可逆的。
     */
    @Override
    public boolean onLongClick(final View view) {
        final ThreadPageInfo info = (ThreadPageInfo) view.getTag();
        new AlertDialog.Builder(getContext())
                .setTitle(info.getSubject())
                .setItems(new CharSequence[]{"移到文件夹", "删除收藏"}, (dialog, which) -> {
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
                .setPositiveButton(android.R.string.ok,
                        (dialog, which) -> mPresenter.removeTopic(info, info.getPosition()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** 单选：现有文件夹 + 新建 + 移出。一个帖子只属于一个文件夹，所以是单选 */
    private void showMoveDialog(final ThreadPageInfo info) {
        final List<String> folders =
                new ArrayList<>(FavoriteStore.getInstance().snapshot().folders);
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
                    if (!FavoriteStore.getInstance().snapshot().createFolder(name)) {
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
        FavoriteStore.getInstance().snapshot().setFolder(info.getTid(), folder);
        FavoriteStore.getInstance().save();
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
                    if (!FavoriteStore.getInstance().snapshot().renameFolder(folder, name)) {
                        ToastUtils.error(name.isEmpty() ? "文件夹名不能为空" : "已存在同名文件夹");
                        return;
                    }
                    FavoriteStore.getInstance().save();
                    if (mCurrentFolder.equals(folder)) {
                        mCurrentFolder = name;
                        applyTitle("收藏夹 - " + name);
                    }
                    renderFolderStrip();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * 删夹只是把里面的帖子放回未分类，对话框必须写清楚这点，
     * 否则用户会以为连收藏一起删了而不敢用。
     */
    private void confirmDeleteFolder(final String folder) {
        new AlertDialog.Builder(getContext())
                .setTitle("删除文件夹「" + folder + "」")
                .setMessage("里面的帖子会回到未分类，收藏本身不会被删除。")
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    FavoriteStore.getInstance().snapshot().deleteFolder(folder);
                    FavoriteStore.getInstance().save();
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

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_topic_favorite, menu);
        SearchView searchView = (SearchView) menu.findItem(R.id.menu_favorite_search).getActionView();
        searchView.setQueryHint("搜索收藏的帖子");
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
        super.onCreateOptionsMenu(menu, inflater);
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

        List<FavoriteItem> hits = FavoriteStore.getInstance().snapshot().search(mSearchKeyword);
        List<ThreadPageInfo> rows = new ArrayList<>();
        for (FavoriteItem hit : hits) {
            ThreadPageInfo info = new ThreadPageInfo();
            info.setTid(hit.tid);
            // 已分类的在标题后缀出所属文件夹，省得还要点进去才知道放哪了
            info.setSubject(hit.folder.isEmpty()
                    ? hit.subject : hit.subject + "  [" + hit.folder + "]");
            info.setAuthor(hit.author);
            info.setFid(hit.fid);
            info.setBoard(hit.board);
            info.setReplies(hit.replies);
            info.setPostDate(hit.postDate);
            rows.add(info);
        }
        mAdapter.setData(null);
        mAdapter.setData(rows);
        updateEmptyText();
    }

    // ==================== 同步全部收藏与空状态 ====================

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.menu_sync_all_favorite) {
            FavoriteSyncTask.execute(() -> {
                renderFolderStrip();
                reloadCurrentView();
            });
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** 空列表要说清楚是哪一种空，否则看起来像收藏丢了 */
    private void updateEmptyText() {
        View root = getView();
        TextView emptyText = root == null ? null : root.findViewById(R.id.tv_empty);
        if (emptyText == null) {
            return;
        }
        if (!mSearchKeyword.isEmpty()) {
            emptyText.setText("没有匹配的收藏");
        } else if (!mCurrentFolder.isEmpty()) {
            emptyText.setText("这个文件夹还没有帖子");
        } else if (!FavoriteStore.getInstance().snapshot().folders.isEmpty()) {
            emptyText.setText("所有收藏都已分类，点上方文件夹查看");
        } else {
            emptyText.setText("还没有收藏任何帖子");
        }
    }
}
