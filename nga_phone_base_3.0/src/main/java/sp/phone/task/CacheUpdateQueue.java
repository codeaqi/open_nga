package sp.phone.task;

import java.util.List;

/**
 * 缓存更新队列的排队逻辑。
 *
 * 单独抽出来是因为这里最容易写错：之前的实现按「最近更新时间」降序取前几个，
 * 而那个时间戳每次检查都会被刷新，于是刚查过的帖子下次排最前，
 * 同一批帖子被反复检查，其余的一次都轮不到。
 *
 * 现在的规则很简单：**所有到期的帖子里，挑距上次检查最久的那个**。
 * 检查完调用方把 lastCheckTime 刷成当前时间，该帖自动落到队尾。
 * 不管会话多短、进程被杀多少次，每个帖子都一定轮得到。
 *
 * 不依赖 Android，可直接单测。
 */
public final class CacheUpdateQueue {

    /** 超过这个时间没有新回复就算老帖 */
    public static final long STALE_THRESHOLD = 7 * 24 * 60 * 60 * 1000L;

    /** 活跃帖两次检查之间的最小间隔，也就是一轮跑完后的冷却时间 */
    public static final long ACTIVE_MIN_INTERVAL = 30 * 60 * 1000L;

    /** 老帖两次检查之间的最小间隔，用于降频 */
    public static final long STALE_MIN_INTERVAL = 6 * 60 * 60 * 1000L;

    private CacheUpdateQueue() {
    }

    /** 参与排队所需的最小信息 */
    public interface Item {

        long getLastCheckTime();

        long getLastChangeTime();
    }

    /**
     * 从未观测过的帖子（老缓存文件没有这个字段，或刚缓存还没检查过）一律算活跃。
     * 否则 0 会被当成 1970 年，新缓存一进队列就被判成老帖，直接降频。
     */
    public static boolean isStale(Item item, long now) {
        long lastChange = item.getLastChangeTime();
        if (lastChange <= 0) {
            return false;
        }
        return now - lastChange > STALE_THRESHOLD;
    }

    public static long minInterval(Item item, long now) {
        return isStale(item, now) ? STALE_MIN_INTERVAL : ACTIVE_MIN_INTERVAL;
    }

    public static boolean isDue(Item item, long now) {
        return now - item.getLastCheckTime() >= minInterval(item, now);
    }

    /**
     * 选出下一个该检查的帖子，没有到期的就返回 null。
     */
    public static <T extends Item> T pickNext(List<T> items, long now) {
        T best = null;
        for (T item : items) {
            if (!isDue(item, now)) {
                continue;
            }
            if (best == null || item.getLastCheckTime() < best.getLastCheckTime()) {
                best = item;
            }
        }
        return best;
    }

    /**
     * 全都没到期时，距最近一个到期还有多久。用来安排下次唤醒，
     * 免得没事干还每 15 秒醒一次。
     */
    public static long nextDueDelay(List<? extends Item> items, long now, long fallback) {
        long min = Long.MAX_VALUE;
        for (Item item : items) {
            long remain = item.getLastCheckTime() + minInterval(item, now) - now;
            if (remain < min) {
                min = remain;
            }
        }
        if (min == Long.MAX_VALUE) {
            return fallback;
        }
        return Math.max(min, 0);
    }
}
