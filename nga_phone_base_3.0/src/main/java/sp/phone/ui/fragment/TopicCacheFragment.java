package sp.phone.ui.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import gov.anzong.androidnga.R;
import gov.anzong.androidnga.activity.ArticleCacheActivity;
import gov.anzong.androidnga.base.util.ToastUtils;
import sp.phone.mvp.model.entity.ThreadPageInfo;
import sp.phone.mvp.model.entity.TopicListInfo;
import sp.phone.param.ArticleListParam;
import sp.phone.param.ParamKey;
import sp.phone.task.TopicCacheUpdateTask;
import sp.phone.ui.adapter.TopicListAdapter;
import sp.phone.util.StringUtils;

/**
 * @author Justwen
 */
public class TopicCacheFragment extends TopicSearchFragment implements View.OnLongClickListener {

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ToastUtils.success("长按可删除缓存的帖子");
        mAdapter.setOnLongClickListener(this);
        if (mAdapter instanceof TopicListAdapter) {
            ((TopicListAdapter) mAdapter).setShowNewReplyTag(true);
        }
        mPresenter.getRemovedTopic().observe(this, this::removeTopic);
    }

    @Override
    public void setData(TopicListInfo result) {
        super.setData(result);
        mAdapter.setNextPageEnabled(false);
        mSwipeRefreshLayout.setEnabled(false);
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
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    ThreadPageInfo info = (ThreadPageInfo) view.getTag();
                    mPresenter.removeCacheTopic(info);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create()
                .show();
        return true;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_cache_list, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
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
