package io.laplace.sprite.core.engine;

import io.laplace.sprite.core.model.GateResult;
import io.laplace.sprite.core.model.MetricSample;
import io.laplace.sprite.core.model.NetworkLatencyDistribution;
import io.laplace.sprite.core.model.Violation;

import java.util.ArrayList;
import java.util.List;

/**
 * 网络延迟门禁（12.3）：P50 &lt; 50ms、P95 &lt; 150ms。
 *
 * <p>两条都是严格小于：实测 50.0ms / 150.0ms 即判失败，49.9ms / 149.9ms 才通过。边界按实测值比较，
 * 不做四舍五入，避免把踩线结果包装成合格。</p>
 */
public final class LatencyGate {

    public GateResult evaluate(NetworkLatencyDistribution distribution) {
        // 没有 Ping 采样时按不适用处理，不能把「没测」当成「延迟优秀」或「延迟超标」。
        if (distribution == null || !distribution.measured()) {
            return GateResult.pass();
        }
        List<Violation> violations = new ArrayList<>();
        if (distribution.p50Ms() >= NetworkLatencyDistribution.P50_LIMIT_MS) {
            violations.add(new Violation("network_latency_p50", "<",
                    distribution.p50Ms(), NetworkLatencyDistribution.P50_LIMIT_MS,
                    "Ping P50 实测 %.1fms，未满足 <%.0fms（%d 个样本）".formatted(
                            distribution.p50Ms(), NetworkLatencyDistribution.P50_LIMIT_MS,
                            distribution.sampleCount())));
        }
        if (distribution.p95Ms() >= NetworkLatencyDistribution.P95_LIMIT_MS) {
            violations.add(new Violation("network_latency_p95", "<",
                    distribution.p95Ms(), NetworkLatencyDistribution.P95_LIMIT_MS,
                    "Ping P95 实测 %.1fms，未满足 <%.0fms（%d 个样本）".formatted(
                            distribution.p95Ms(), NetworkLatencyDistribution.P95_LIMIT_MS,
                            distribution.sampleCount())));
        }
        return violations.isEmpty() ? GateResult.pass() : GateResult.fail(violations);
    }

    /**
     * 从采样窗口评估：每个窗口只上报 P50/P95，跨窗口取最差值。
     *
     * <p>原始 ping 样本没有随 MetricSample 一起保留，因此不能重新计算分位；取最差窗口是保守口径，
     * 能抓住尖峰，也不会把多个窗口平均成一个好看的数。</p>
     */
    public GateResult evaluate(List<MetricSample> samples) {
        if (samples == null) {
            return GateResult.pass();
        }
        double p50 = 0.0;
        double p95 = 0.0;
        int count = 0;
        for (MetricSample sample : samples) {
            if (sample != null && sample.hasLatencySample()) {
                p50 = Math.max(p50, sample.pingP50Ms());
                p95 = Math.max(p95, sample.pingP95Ms());
                count++;
            }
        }
        return evaluate(new NetworkLatencyDistribution(p50, p95, count));
    }
}
