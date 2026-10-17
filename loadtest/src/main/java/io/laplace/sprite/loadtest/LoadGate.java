package io.laplace.sprite.loadtest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 硬门禁（15.9）：稳态 TPS ≥ 19.5 且 P95 MSPT ≤ 50ms。
 *
 * <p>阈值直接取自设计文档的示例门禁，与 {@code LongRunWatchdog} 的 19.5 同源；
 * 两个边界都是闭区间，19.5 与 50.0 本身算通过。</p>
 *
 * <p>稳态窗口内取最差值（最低 TPS、最高 P95）而不是平均值，避免长时间均值掩盖尖刺。
 * 没有任何稳态采样时不判通过：没有数据就不能声称达标。</p>
 */
public final class LoadGate {

    public static final double MIN_STEADY_TPS = 19.5;
    public static final double MAX_P95_MSPT_MS = 50.0;

    /** 单点判据，测试与报告共用。 */
    public static boolean passes(double tps, double msptP95) {
        return tps >= MIN_STEADY_TPS && msptP95 <= MAX_P95_MSPT_MS;
    }

    public LoadVerdict evaluate(LoadScenario scenario, LoadTimeline timeline) {
        List<String> violations = new ArrayList<>();
        Map<Integer, LoadVerdict.CurvePoint> pointsByConcurrency = new LinkedHashMap<>();
        Integer breakingConcurrency = null;

        for (LoadTimeline.Window window : timeline.windows(scenario)) {
            if (window.empty()) {
                continue;
            }
            double tps = window.tpsMin();
            double p95 = window.msptP95Max();
            boolean ok = passes(tps, p95);
            if (!ok) {
                if (breakingConcurrency == null) {
                    breakingConcurrency = window.concurrency();
                }
                if (tps < MIN_STEADY_TPS) {
                    violations.add("并发 %d：稳态 TPS %.2f < %.1f"
                            .formatted(window.concurrency(), tps, MIN_STEADY_TPS));
                }
                if (p95 > MAX_P95_MSPT_MS) {
                    violations.add("并发 %d：P95 MSPT %.2fms > %.1fms"
                            .formatted(window.concurrency(), p95, MAX_P95_MSPT_MS));
                }
            }
            // 并发抬到上限后会连续出现同一档；曲线取该档所有台阶的最差值
            LoadVerdict.CurvePoint previous = pointsByConcurrency.get(window.concurrency());
            if (previous == null) {
                pointsByConcurrency.put(window.concurrency(),
                        new LoadVerdict.CurvePoint(window.concurrency(), tps, p95, ok));
            } else {
                pointsByConcurrency.put(window.concurrency(), new LoadVerdict.CurvePoint(
                        window.concurrency(), Math.min(previous.tps(), tps),
                        Math.max(previous.msptP95(), p95), previous.passed() && ok));
            }
        }

        List<LoadVerdict.CurvePoint> points = List.copyOf(pointsByConcurrency.values());
        if (points.isEmpty()) {
            violations.add("没有可判定的稳态采样，门禁不下通过结论");
        }
        return new LoadVerdict(breakingConcurrency == null && violations.isEmpty(), breakingConcurrency,
                violations, points);
    }
}
