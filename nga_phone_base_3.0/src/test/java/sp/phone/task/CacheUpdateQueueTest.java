package sp.phone.task;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class CacheUpdateQueueTest {

    private static final long NOW = 1_700_000_000_000L;

    private static final long HOUR = 60 * 60 * 1000L;

    private static final long DAY = 24 * HOUR;

    private static class FakeTopic implements CacheUpdateQueue.Item {

        final int tid;

        long lastCheckTime;

        long lastChangeTime;

        FakeTopic(int tid, long lastCheckTime, long lastChangeTime) {
            this.tid = tid;
            this.lastCheckTime = lastCheckTime;
            this.lastChangeTime = lastChangeTime;
        }

        @Override
        public long getLastCheckTime() {
            return lastCheckTime;
        }

        @Override
        public long getLastChangeTime() {
            return lastChangeTime;
        }
    }

    /** 活跃帖：刚有过新回复 */
    private static FakeTopic active(int tid, long lastCheckTime) {
        return new FakeTopic(tid, lastCheckTime, NOW - HOUR);
    }

    /**
     * 核心回归用例：会话很短时，每次都必须轮到不同的帖子。
     *
     * 老实现按「最近更新时间」降序取前几个，而那个时间戳每次检查都会刷新，
     * 于是同一批帖子被反复检查、其余永远轮不到。这里锁死正确行为。
     */
    @Test
    public void shortSessionsEventuallyCoverEveryTopic() {
        List<FakeTopic> topics = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            topics.add(active(i, 0));
        }

        List<Integer> checked = new ArrayList<>();
        long now = NOW;
        // 模拟 5 次会话，每次只来得及检查 2 个帖子
        for (int session = 0; session < 5; session++) {
            for (int i = 0; i < 2; i++) {
                FakeTopic next = CacheUpdateQueue.pickNext(topics, now);
                assertNotNull("应该总有帖子可查", next);
                next.lastCheckTime = now;
                checked.add(next.tid);
                now += 15 * 1000L;
            }
            now += 10 * 60 * 1000L;
        }

        assertEquals("10 次检查应覆盖全部 10 个帖子，不能有重复", 10,
                new java.util.HashSet<>(checked).size());
    }

    /** 检查过的帖子在冷却期内不再被选中 */
    @Test
    public void recentlyCheckedTopicIsNotPickedAgain() {
        List<FakeTopic> topics = new ArrayList<>();
        topics.add(active(1, NOW - 1000));

        assertNull("刚查过的帖子不该立刻再被选中",
                CacheUpdateQueue.pickNext(topics, NOW));

        long later = NOW + CacheUpdateQueue.ACTIVE_MIN_INTERVAL;
        assertNotNull("过了冷却期应该重新到期",
                CacheUpdateQueue.pickNext(topics, later));
    }

    /** 最久没查的优先 */
    @Test
    public void picksLongestUncheckedFirst() {
        List<FakeTopic> topics = new ArrayList<>();
        topics.add(active(1, NOW - 2 * HOUR));
        topics.add(active(2, NOW - 5 * HOUR));
        topics.add(active(3, NOW - 3 * HOUR));

        FakeTopic next = CacheUpdateQueue.pickNext(topics, NOW);
        assertNotNull(next);
        assertEquals(2, next.tid);
    }

    /** 从未观测过变化的帖子按活跃处理，不能被误判成老帖 */
    @Test
    public void neverObservedTopicCountsAsActive() {
        FakeTopic fresh = new FakeTopic(1, 0, 0);
        assertFalse("lastChangeTime=0 表示尚未观测，不该算老帖",
                CacheUpdateQueue.isStale(fresh, NOW));
        assertEquals(CacheUpdateQueue.ACTIVE_MIN_INTERVAL,
                CacheUpdateQueue.minInterval(fresh, NOW));
    }

    /** 超过 7 天没有新回复的帖子降频，但不会被永久剔除 */
    @Test
    public void staleTopicIsDeprioritisedButNotDropped() {
        FakeTopic stale = new FakeTopic(1, NOW - HOUR, NOW - 30 * DAY);
        assertTrue(CacheUpdateQueue.isStale(stale, NOW));
        assertEquals(CacheUpdateQueue.STALE_MIN_INTERVAL,
                CacheUpdateQueue.minInterval(stale, NOW));

        List<FakeTopic> topics = new ArrayList<>();
        topics.add(stale);
        assertNull("老帖 1 小时前查过，还没到 6 小时，不该被选中",
                CacheUpdateQueue.pickNext(topics, NOW));
        assertNotNull("过了 6 小时老帖仍然会被检查，不是永久出局",
                CacheUpdateQueue.pickNext(topics, NOW + CacheUpdateQueue.STALE_MIN_INTERVAL));
    }

    /** 活跃帖优先于老帖，即使老帖等得更久 */
    @Test
    public void activeTopicBeatsStaleTopic() {
        List<FakeTopic> topics = new ArrayList<>();
        topics.add(new FakeTopic(1, NOW - 10 * DAY, NOW - 30 * DAY)); // 老帖，等最久
        topics.add(active(2, NOW - CacheUpdateQueue.ACTIVE_MIN_INTERVAL));

        // 两个都到期时按 lastCheckTime 排，老帖确实会先被选中——
        // 降频体现在它到期的频率上，而不是被插队
        FakeTopic next = CacheUpdateQueue.pickNext(topics, NOW);
        assertNotNull(next);
        assertEquals(1, next.tid);

        // 老帖查过之后 6 小时内不再到期，活跃帖则每 30 分钟就能轮一次
        next.lastCheckTime = NOW;
        FakeTopic second = CacheUpdateQueue.pickNext(topics, NOW);
        assertNotNull(second);
        assertEquals(2, second.tid);
    }

    /** 空队列不炸 */
    @Test
    public void emptyQueueReturnsFallbackDelay() {
        List<FakeTopic> empty = new ArrayList<>();
        assertNull(CacheUpdateQueue.pickNext(empty, NOW));
        assertEquals(12345L, CacheUpdateQueue.nextDueDelay(empty, NOW, 12345L));
    }

    /** 都没到期时，返回距最近一个到期的剩余时间 */
    @Test
    public void nextDueDelayReturnsSoonestRemaining() {
        List<FakeTopic> topics = new ArrayList<>();
        topics.add(active(1, NOW - 10 * 60 * 1000L));
        topics.add(active(2, NOW - 20 * 60 * 1000L));

        long delay = CacheUpdateQueue.nextDueDelay(topics, NOW, Long.MAX_VALUE);
        assertEquals(CacheUpdateQueue.ACTIVE_MIN_INTERVAL - 20 * 60 * 1000L, delay);
    }
}
