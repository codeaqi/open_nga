package sp.phone.ui.fragment;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import gov.anzong.androidnga.R;
import gov.anzong.androidnga.activity.ArticleCacheActivity;
import gov.anzong.androidnga.base.util.ToastUtils;
import gov.anzong.androidnga.cache.CacheFolderStore;
import gov.anzong.androidnga.folder.FolderRepository;
import sp.phone.mvp.model.entity.ThreadPageInfo;
import sp.phone.mvp.model.entity.TopicListInfo;
import sp.phone.param.ArticleListParam;
import sp.phone.param.ParamKey;
import sp.phone.task.TopicCacheUpdateTask;
import sp.phone.ui.adapter.TopicListAdapter;
import sp.phone.util.StringUtils;

/**
 * 我的缓存。
 *
 * 文件夹条、归类、搜索与收藏夹共用 {@link TopicFolderFragment}，
 * 但分类是**另一套**，存在 CacheFolderStore 里，跟收藏夹互不影响。
 *
 * @author Justwen
 */
public class TopicCacheFragment extends TopicFolderFragment {

    @Override
    protected FolderRepository folderStore() {
        return CacheFolderStore.getInstance();
    }

    @Override
    protected String itemNoun() {
        return "缓存";
    }

    @Override
    protected String rootTitle() {
        return "我的缓存";
    }

    @Override
    protected String folderTitle(String folder) {
        return "我的缓存 - " + folder;
    }

    @Override
    protected void deleteItem(ThreadPageInfo info) {
        CacheFolderStore.getInstance().snapshot().forget(info.getTid());
        CacheFolderStore.getInstance().save();
        mPresenter.removeCacheTopic(info);
    }

    /**
     * 缓存列表是一次性把 cache 目录全量读出来的，所以候选就是已加载的整份列表，
     * 不像收藏夹那样还要另存一份快照。
     */
    @Override
    protected List<ThreadPageInfo> searchCandidates() {
        if (mTopicListInfo == null || mTopicListInfo.getThreadPageList() == null) {
            return new ArrayList<>();
        }
        return mTopicListInfo.getThreadPageList();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        CacheFolderStore.getInstance().load();
        ToastUtils.success("长按帖子可归类或删除缓存");
        if (mAdapter instanceof TopicListAdapter) {
            ((TopicListAdapter) mAdapter).setShowNewReplyTag(true);
        }
        mPresenter.getRemovedTopic().observe(this, this::removeTopic);
        renderFolderStrip();
    }

    /**
     * 磁盘上现存的帖子就是全集，顺手把指向已删缓存的归类记录清掉，
     * 否则那些记录会一直躺在文件里。
     */
    @Override
    public void setData(TopicListInfo result) {
        mTopicListInfo = result;
        List<ThreadPageInfo> pageList = result.getThreadPageList();
        pruneFolders(pageList);
        mAdapter.setData(filterForCurrentView(pageList));
        mAdapter.setNextPageEnabled(false);
        mSwipeRefreshLayout.setEnabled(false);
        renderFolderStrip();
        updateEmptyText();
    }

    private void pruneFolders(List<ThreadPageInfo> pageList) {
        if (pageList == null || pageList.isEmpty()) {
            return;
        }
        Set<Integer> tids = new HashSet<>();
        for (ThreadPageInfo info : pageList) {
            tids.add(info.getTid());
        }
        CacheFolderStore.getInstance().snapshot().pruneTo(tids);
        CacheFolderStore.getInstance().save();
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
        inflater.inflate(R.menu.menu_cache_list, menu);
        bindSearchView(menu, R.id.menu_cache_search);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_cache_export:
                mPresenter.exportCacheTopic(this);
                break;
            case R.id.menu_cache_import:
                mPresenter.showFileChooser(this);
                break;
            case R.id.menu_cache_update:
                updateCacheTopics();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    /**
     * 手动检查所有缓存帖子有没有新回复。
     *
     * 后台调度器是限速的（每 15 秒一个请求，慢慢轮转），这里是用户主动触发的全量检查，
     * 不限速地把所有帖子跑一遍，图的是立刻出结果。执行期间调度器会自动让路。
     */
    private void updateCacheTopics() {
        ToastUtils.info("正在检查新回复…");
        TopicCacheUpdateTask.execute(hasUpdate -> {
            if (!isAdded()) {
                return;
            }
            if (hasUpdate) {
                ToastUtils.success("有新回复，已更新");
                // 重新读一遍缓存列表，新回复角标才会显示出来
                mPresenter.loadPage(1, mRequestParam);
            } else {
                ToastUtils.info("没有新回复");
            }
        });
    }

    @Override
    public void onClick(View view) {
        ThreadPageInfo info = (ThreadPageInfo) view.getTag();
        if (info.getNewReplyCount() > 0) {
            // 看过就不再提示，同时更新内存与磁盘
            info.setNewReplyCount(0);
            TopicCacheUpdateTask.clearNewReplyTag(info.getTid());
            mAdapter.notifyItemChanged(info.getPosition());
        }
        ArticleListParam param = new ArticleListParam();
        param.tid = info.getTid();
        param.loadCache = true;
        param.title = StringUtils.unEscapeHtml(info.getSubject());
        Intent intent = new Intent();
        Bundle bundle = new Bundle();
        bundle.putParcelable(ParamKey.KEY_PARAM, param);
        intent.putExtras(bundle);
        intent.setClass(getContext(), ArticleCacheActivity.class);
        startActivity(intent);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_IMPORT_CACHE && resultCode == Activity.RESULT_OK) {
            if (data == null) {
                return;
            }
            mPresenter.importCacheTopic(data.getData());
        } else if (requestCode == REQUEST_EXPORT_CACHE && resultCode == Activity.RESULT_OK) {
            if (data == null) {
                return;
            }
            mPresenter.exportCacheTopic(data.getData());
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
}
