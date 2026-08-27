package sp.phone.ui.fragment;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import gov.anzong.androidnga.R;
import gov.anzong.androidnga.base.util.ToastUtils;
import gov.anzong.androidnga.favorite.FavoriteStore;
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
        if (folders.isEmpty()) {
            mFolderScroll.setVisibility(View.GONE);
            return;
        }
        mFolderScroll.setVisibility(View.VISIBLE);
        for (String folder : folders) {
            mFolderStrip.addView(createFolderChip(folder));
        }
    }

    private TextView createFolderChip(final String folder) {
        TextView chip = new TextView(getContext());
        chip.setText("📁 " + folder);
        chip.setTextSize(14);
        chip.setPadding(24, 12, 24, 12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = 12;
        chip.setLayoutParams(lp);
        return chip;
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
}
