package io.laplace.sprite.sandbox;

import java.time.Duration;

/**
 * OOM 退避策略（15.4 OOM 自愈）：容器被 OOM killer 终止后按指数退避重启，但重启名额有限。
 *
 * <p>退避只是让「偶发内存峰值」有机会跑完，绝不改变判定：被 OOM 杀过的用例一律按内存不足上报。</p>
 *
 * @param initialDelay 首次重启等待
 * @param multiplier   每次重启的等待倍增系数
 * @param maxDelay     等待上限
 * @param maxRestarts  重启次数上限
 */
public record OomBackoffPolicy(Duration initialDelay, double multiplier, Duration maxDelay, int maxRestarts) {

    public OomBackoffPolicy {
        if (initialDelay == null || initialDelay.isZero() || initialDelay.isNegative()) {
            throw new IllegalArgumentException("初始退避必须为正");
        }
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("倍增系数不能小于 1: " + multiplier);
        }
        if (maxDelay == null || maxDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException("等待上限不能小于初始退避");
        }
        if (maxRestarts < 0) {
            throw new IllegalArgumentException("重启上限不能为负数: " + maxRestarts);
        }
    }

    public static OomBackoffPolicy defaults() {
        return new OomBackoffPolicy(Duration.ofSeconds(1), 2.0, Duration.ofSeconds(30), 3);
    }

    /** 第 attempt 次重启（从 1 开始）的等待时长，超过上限即封顶。 */
    public Duration delayFor(int attempt) {
        if (attempt < 1) {
            throw new IllegalArgumentException("重启序号从 1 开始: " + attempt);
        }
        long base = initialDelay.toMillis();
        double factor = Math.pow(multiplier, attempt - 1);
        long delay = Math.max(base, Math.round(base * factor));
        return Duration.ofMillis(Math.min(maxDelay.toMillis(), delay));
    }

    public boolean hasBudget(int completedRestarts) {
        return completedRestarts < maxRestarts;
    }
}
