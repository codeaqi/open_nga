package sp.phone.ui.fragment;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import gov.anzong.androidnga.R;
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

    @Override
    public boolean onLongClick(final View view) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setMessage(this.getString(R.string.delete_favo_confirm_text))
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ThreadPageInfo info = (ThreadPageInfo) view.getTag();
                        mPresenter.removeTopic(info, info.getPosition());
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create()
                .show();
        return true;
    }
}
