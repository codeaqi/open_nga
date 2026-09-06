package sp.phone.ui.fragment;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import gov.anzong.androidnga.R;
import gov.anzong.androidnga.favorite.FavoriteItem;
import gov.anzong.androidnga.favorite.FavoriteStore;
import gov.anzong.androidnga.folder.FolderRepository;
import sp.phone.task.FavoriteSyncTask;
import sp.phone.mvp.model.entity.ThreadPageInfo;
import sp.phone.mvp.model.entity.TopicListInfo;

/**
 * 收藏夹。
 *
 * 服务端只给一个平铺的收藏列表，文件夹是本地的（见 FavoriteStore）。
 * 文件夹条、归类、搜索这些界面逻辑在 {@link TopicFolderFragment}，和「我的缓存」共用。
 */
public class TopicFavoriteFragment extends TopicFolderFragment {

    @Override
    protected void setTitle() {
        setTitle(R.string.bookmark_title);
    }

    @Override
    protected FolderRepository folderStore() {
        return FavoriteStore.getInstance();
    }

    @Override
    protected String itemNoun() {
        return "收藏";
    }

    @Override
    protected String rootTitle() {
        return getString(R.string.bookmark_title);
    }

    @Override
    protected String folderTitle(String folder) {
        return "收藏夹 - " + folder;
    }

    @Override
    protected void deleteItem(ThreadPageInfo info) {
        mPresenter.removeTopic(info, info.getPosition());
    }

    /**
     * 搜索的候选是**本地快照的全部收藏**，不是当前加载的那几页——
     * 快照里存了同步过的所有收藏，这正是它存在的意义。
     */
    @Override
    protected List<ThreadPageInfo> searchCandidates() {
        List<ThreadPageInfo> candidates = new ArrayList<>();
        for (FavoriteItem item : FavoriteStore.getInstance().snapshot().items.values()) {
            ThreadPageInfo info = new ThreadPageInfo();
            info.setTid(item.tid);
            info.setSubject(item.subject);
            info.setAuthor(item.author);
            info.setFid(item.fid);
            info.setBoard(item.board);
            info.setReplies(item.replies);
            info.setPostDate(item.postDate);
            candidates.add(info);
        }
        return candidates;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FavoriteStore.getInstance().load();
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

    @Override
    public void removeTopic(int position) {
        mAdapter.removeItem(position);
    }

    @Override
    public void removeTopic(ThreadPageInfo pageInfo) {
        mAdapter.removeItem(pageInfo);
    }

    // ==================== 菜单 ====================

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_topic_favorite, menu);
        bindSearchView(menu, R.id.menu_favorite_search);
        super.onCreateOptionsMenu(menu, inflater);
    }

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
}
