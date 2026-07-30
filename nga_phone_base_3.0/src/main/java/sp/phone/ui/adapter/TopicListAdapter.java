package sp.phone.ui.adapter;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import gov.anzong.androidnga.R;
import gov.anzong.androidnga.base.util.ContextUtils;
import sp.phone.common.PhoneConfiguration;
import sp.phone.mvp.model.entity.ThreadPageInfo;
import sp.phone.param.TopicTitleHelper;
import sp.phone.rxjava.RxUtils;
import sp.phone.theme.ThemeManager;

public class TopicListAdapter extends BaseAppendableAdapter<ThreadPageInfo, TopicListAdapter.TopicViewHolder> {

    /** 是否显示新回复标记，仅缓存列表需要 */
    private boolean mShowNewReplyTag;

    public TopicListAdapter(Context context) {
        super(context);
    }

    public void setShowNewReplyTag(boolean showNewReplyTag) {
        mShowNewReplyTag = showNewReplyTag;
    }

    @Override
    public TopicViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        TopicViewHolder viewHolder = new TopicViewHolder(LayoutInflater.from(mContext).inflate(R.layout.list_topic, parent, false));
        viewHolder.title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, PhoneConfiguration.getInstance().getTopicTitleSize());
        RxUtils.clicks(viewHolder.itemView, mOnClickListener);
        viewHolder.itemView.setOnLongClickListener(mOnLongClickListener);
        return viewHolder;
    }

    @Override
    public void setData(List<ThreadPageInfo> dataList) {
        if (dataList == null) {
            super.setData(null);
        } else {
            super.appendData(dataList);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull TopicViewHolder holder, int position) {

        ThreadPageInfo info = getItem(position);
        info.setPosition(position);
        holder.itemView.setTag(info);

        handleJsonList(holder, info);
        if (!PhoneConfiguration.getInstance().useSolidColorBackground()) {
            holder.itemView.setBackgroundResource(ThemeManager.getInstance().getBackgroundColor(position));
        }
    }

    private void handleJsonList(TopicViewHolder holder, ThreadPageInfo entry) {

        if (entry == null) {
            return;
        }
        holder.author.setText(entry.getAuthor());
        holder.lastReply.setText(entry.getLastPoster());
        holder.num.setText(String.valueOf(entry.getReplies()));

        CharSequence title = TopicTitleHelper.handleTitleFormat(entry);
        if (mShowNewReplyTag && entry.getNewReplyCount() > 0) {
            title = appendNewReplyTag(title, entry.getNewReplyCount());
        }
        holder.title.setText(title);
    }

    /**
     * 在标题后追加新回复数标记，样式与标题中的 [锁定]、[合集] 保持一致
     */
    private CharSequence appendNewReplyTag(CharSequence title, int count) {
        SpannableStringBuilder builder = new SpannableStringBuilder(title);
        String tag = " [+" + count + "]";
        builder.append(tag);
        builder.setSpan(new ForegroundColorSpan(ContextUtils.getColor(R.color.title_red)),
                builder.length() - tag.length(), builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return builder;
    }

    public class TopicViewHolder extends RecyclerView.ViewHolder {

        @BindView(R.id.num)
        public TextView num;

        @BindView(R.id.title)
        public TextView title;

        @BindView(R.id.author)
        public TextView author;

        @BindView(R.id.last_reply)
        public TextView lastReply;

        public TopicViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }
    }
}
