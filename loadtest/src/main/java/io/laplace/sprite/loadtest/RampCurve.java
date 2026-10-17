package io.laplace.sprite.loadtest;

import java.util.ArrayList;
import java.util.List;

/**
 * 爬坡曲线（15.9）：从起始并发按固定步长、固定台阶时长抬到上限。
 *
 * @param startConcurrency 起始并发
 * @param step             每个台阶增加的并发，0 表示恒定并发
 * @param stepTicks        单个台阶持续的 tick 数
 * @param maxConcurrency   并发上限
 */
public record RampCurve(int startConcurrency, int step, int stepTicks, int maxConcurrency) {

    public RampCurve {
        if (startConcurrency <= 0) {
            throw new IllegalArgumentException("起始并发必须为正数: " + startConcurrency);
        }
        if (step < 0) {
            throw new IllegalArgumentException("爬坡步长不能为负数: " + step);
        }
        if (stepTicks <= 0) {
            throw new IllegalArgumentException("台阶时长必须为正数: " + stepTicks);
        }
        if (maxConcurrency < startConcurrency) {
            throw new IllegalArgumentException("并发上限不能低于起始并发: " + maxConcurrency + " < "
                    + startConcurrency);
        }
    }

    /** 恒定并发：单假人回放等场景直接引用；台阶时长取满，schedule 只会切出一段。 */
    public static RampCurve single(int concurrency) {
        return new RampCurve(concurrency, 0, Integer.MAX_VALUE, concurrency);
    }

    /**
     * 一个台阶。
     *
     * @param index       台阶序号，从 0 开始
     * @param startTick   起始 tick（含）
     * @param endTick     结束 tick（不含）
     * @param concurrency 该台阶的并发
     */
    public record RampStep(int index, int startTick, int endTick, int concurrency) {

        public RampStep {
            if (startTick < 0 || endTick <= startTick) {
                throw new IllegalArgumentException("台阶区间不合法: [" + startTick + ", " + endTick + ")");
            }
            if (concurrency <= 0) {
                throw new IllegalArgumentException("台阶并发必须为正数: " + concurrency);
            }
        }

        public int ticks() {
            return endTick - startTick;
        }
    }

    /** 按总时长摊出全部台阶；并发抬到上限后保持到结束。 */
    public List<RampStep> schedule(int durationTicks) {
        if (durationTicks <= 0) {
            throw new IllegalArgumentException("压测时长必须为正数: " + durationTicks);
        }
        List<RampStep> steps = new ArrayList<>();
        int startTick = 0;
        int concurrency = startConcurrency;
        int index = 0;
        while (startTick < durationTicks) {
            int endTick = (int) Math.min((long) startTick + stepTicks, durationTicks);
            steps.add(new RampStep(index++, startTick, endTick, concurrency));
            startTick = endTick;
            concurrency = Math.min(maxConcurrency, concurrency + step);
        }
        return List.copyOf(steps);
    }

    /** 指定 tick 所在台阶的并发；越界时返回最后一个台阶的并发。 */
    public int concurrencyAt(int tick, int durationTicks) {
        List<RampStep> steps = schedule(durationTicks);
        for (RampStep step : steps) {
            if (tick < step.endTick()) {
                return step.concurrency();
            }
        }
        return steps.get(steps.size() - 1).concurrency();
    }
}
