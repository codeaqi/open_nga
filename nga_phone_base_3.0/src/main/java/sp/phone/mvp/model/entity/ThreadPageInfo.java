package sp.phone.mvp.model.entity;

import androidx.annotation.NonNull;

import gov.anzong.androidnga.common.base.JavaBean;

public class ThreadPageInfo implements JavaBean {

    private int mTid;

    private String mAuthor;

    private int mFid;

    private int mAuthorId;

    private String mLastPoster;

    private int mReplies;

    private String mSubject;

    private String mTitleFont;

    private int mType;

    private String mTopicMisc;

    private int mPage;

    private int mPid;

    private int mPosition;

    private boolean mIsAnonymity;

    private int mPostDate;

    private ReplyInfo mReplyInfo;

    private String mBoard;

    /**
     * 是否是版面镜像
     */
    private boolean mMirrorBoard;

    /**
     * 缓存更新后新增的回复数，仅用于本地缓存列表的提示，读过后清零
     */
    private int mNewReplyCount;

    /**
     * 上次轮询该帖的时间。缓存更新队列按它升序排，最久没查的优先，
     * 检查完刷成当前时间就自动排到队尾，保证每个帖子都轮得到。
     * 0 表示从未查过，排最前面。
     */
    private long mLastCheckTime;

    /**
     * 上次发现回复数增加的时间，用来区分活跃帖和老帖。
     * 不能拿文件修改时间代替——那个每次检查都会被刷新。
     * 0 表示尚未观测过，一律按活跃帖处理，免得新缓存被误判成老帖。
     */
    private long mLastChangeTime;

    public int getNewReplyCount() {
        return mNewReplyCount;
    }

    public void setNewReplyCount(int newReplyCount) {
        mNewReplyCount = newReplyCount;
    }

    public long getLastCheckTime() {
        return mLastCheckTime;
    }

    public void setLastCheckTime(long lastCheckTime) {
        mLastCheckTime = lastCheckTime;
    }

    public long getLastChangeTime() {
        return mLastChangeTime;
    }

    public void setLastChangeTime(long lastChangeTime) {
        mLastChangeTime = lastChangeTime;
    }

    public boolean isMirrorBoard() {
        return mMirrorBoard;
    }

    public int getPostDate() {
        return mPostDate;
    }

    public void setPostDate(int postDate) {
        mPostDate = postDate;
    }

    public int getTid() {
        return mTid;
    }

    public void setTid(int tid) {
        mTid = tid;
    }

    public String getAuthor() {
        return mAuthor;
    }

    public void setAuthor(String author) {
        mAuthor = author;
    }

    public int getFid() {
        return mFid;
    }

    public void setFid(int fid) {
        mFid = fid;
    }

    public int getAuthorId() {
        return mAuthorId;
    }

    public void setAuthorId(int authorId) {
        mAuthorId = authorId;
    }

    public String getLastPoster() {
        return mLastPoster;
    }

    public void setLastPoster(String lastPoster) {
        mLastPoster = lastPoster;
    }

    public int getReplies() {
        return mReplies;
    }

    public void setReplies(int replies) {
        mReplies = replies;
    }

    public String getSubject() {
        return mSubject;
    }

    public void setSubject(String subject) {
        mSubject = subject;
    }

    public String getTitleFont() {
        return mTitleFont;
    }

    public void setTitleFont(String titleFont) {
        mTitleFont = titleFont;
    }

    public int getType() {
        return mType;
    }

    public void setType(int type) {
        mType = type;
    }

    public String getTopicMisc() {
        return mTopicMisc;
    }

    public void setTopicMisc(String topicMisc) {
        mTopicMisc = topicMisc;
    }

    public int getPage() {
        return mPage;
    }

    public void setPage(int page) {
        mPage = page;
    }

    public int getPid() {
        return mPid;
    }

    public void setPid(int pid) {
        mPid = pid;
    }

    public int getPosition() {
        return mPosition;
    }

    public void setPosition(int position) {
        mPosition = position;
    }

    public boolean isAnonymity() {
        return mIsAnonymity;
    }

    public void setAnonymity(boolean anonymity) {
        mIsAnonymity = anonymity;
    }

    public ReplyInfo getReplyInfo() {
        return mReplyInfo;
    }

    public void setReplyInfo(ReplyInfo replyInfo) {
        mReplyInfo = replyInfo;
    }

    public String getBoard() {
        return mBoard;
    }

    public void setBoard(String parentBoard) {
        mBoard = parentBoard;
        mMirrorBoard = "版面镜像".equals(parentBoard);
    }

    public static class ReplyInfo implements JavaBean {

        private String mPidStr;

        private String mContent;

        private String mSubject;

        private String mPostDate;

        private String mAuthorId;

        private String mTidStr;

        public String getPidStr() {
            return mPidStr;
        }

        public void setPidStr(String pidStr) {
            mPidStr = pidStr;
        }

        public String getContent() {
            return mContent;
        }

        public void setContent(String content) {
            mContent = content;
        }

        public String getSubject() {
            return mSubject;
        }

        public void setSubject(String subject) {
            mSubject = subject;
        }

        public String getPostDate() {
            return mPostDate;
        }

        public void setPostDate(String postDate) {
            mPostDate = postDate;
        }

        public String getAuthorId() {
            return mAuthorId;
        }

        public void setAuthorId(String authorId) {
            mAuthorId = authorId;
        }

        public String getTidStr() {
            return mTidStr;
        }

        public void setTidStr(String tidStr) {
            mTidStr = tidStr;
        }
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ThreadPageInfo
                && mTid == ((ThreadPageInfo) obj).getTid()
                && mPid == ((ThreadPageInfo) obj).getPid();
    }

    @NonNull
    @Override
    public String toString() {
        return "tid = " + mTid + "  pid = " + mPid;
    }
}
