package io.laplace.sprite.loadtest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 压测指标时间轴（15.9）。
 *
 * <p>稳态窗口按台阶计算：每个台阶丢掉前 {@code rampInTicks} 个 tick 的爬坡段，
 * 只保留台阶后半段的采样。台阶比爬坡段还短时窗口为空，不补数据、不猜趋势。</p>
 */
public final class LoadTimeline {

    /** 默认每台阶丢弃 20 tick（1 秒）作为爬坡段。 */
    public static final int DEFAULT_RAMP_IN_TICKS = 20;

    private final int rampInTicks;
    private final List<LoadSample> samples = new ArrayList<>();

    public LoadTimeline() {
        this(DEFAULT_RAMP_IN_TICKS);
    }

    public LoadTimeline(int rampInTicks) {
        if (rampInTicks < 0) {
            throw new IllegalArgumentException("爬坡段不能为负数: " + rampInTicks);
        }
        this.rampInTicks = rampInTicks;
    }

    public int rampInTicks() {
        return rampInTicks;
    }

    public void add(LoadSample sample) {
        samples.add(Objects.requireNonNull(sample, "sample"));
    }

    public void addAll(Collection<LoadSample> batch) {
        batch.forEach(this::add);
    }

    public List<LoadSample> samples() {
        return List.copyOf(samples);
    }

    /**
     * 一个台阶的稳态窗口。
     *
     * @param stepIndex   台阶序号
     * @param concurrency 台阶并发
     * @param startTick   稳态窗口起点（含）
     * @param endTick     稳态窗口终点（不含）
     * @param samples     窗口内采样
     */
    public record Window(int stepIndex, int concurrency, int startTick, int endTick,
                         List<LoadSample> samples) {

        public Window {
            samples = samples == null ? List.of() : List.copyOf(samples);
        }

        public boolean empty() {
            return samples.isEmpty();
        }

        /** 窗口内最差 TPS；空窗口返回 0，调用方应先看 {@link #empty()}。 */
        public double tpsMin() {
            return samples.stream().mapToDouble(LoadSample::tps).min().orElse(0.0);
        }

        /** 窗口内最差 P95 MSPT；空窗口返回 0。 */
        public double msptP95Max() {
            return samples.stream().mapToDouble(LoadSample::msptP95).max().orElse(0.0);
        }

        public double msptP50Average() {
            return samples.stream().mapToDouble(LoadSample::msptP50).average().orElse(0.0);
        }

        public double msptMax() {
            return samples.stream().mapToDouble(LoadSample::msptMax).max().orElse(0.0);
        }

        public double tpsAverage() {
            return samples.stream().mapToDouble(LoadSample::tps).average().orElse(0.0);
        }
    }

    /** 按剧本台阶切出稳态窗口；采样按 tick 落在窗口内且并发一致才算。 */
    public List<Window> windows(LoadScenario scenario) {
        Objects.requireNonNull(scenario, "scenario");
        List<Window> windows = new ArrayList<>();
        for (RampCurve.RampStep step : scenario.schedule()) {
            int steadyStart = step.startTick() + rampInTicks;
            if (steadyStart >= step.endTick()) {
                windows.add(new Window(step.index(), step.concurrency(), steadyStart, step.endTick(),
                        List.of()));
                continue;
            }
            List<LoadSample> inWindow = samples.stream()
                    .filter(sample -> sample.concurrency() == step.concurrency())
                    .filter(sample -> sample.tick() >= steadyStart && sample.tick() < step.endTick())
                    .toList();
            windows.add(new Window(step.index(), step.concurrency(), steadyStart, step.endTick(), inWindow));
        }
        return List.copyOf(windows);
    }
}
