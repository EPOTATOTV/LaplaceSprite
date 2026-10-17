package io.laplace.sprite.core.model;

import io.laplace.sprite.core.engine.LatencyGate;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 12.3 网络延迟分布与门禁。 */
class NetworkLatencyTest {

    @Test
    void nearestRankPicksExistingSamplesWithoutInterpolation() {
        NetworkLatencyDistribution distribution =
                NetworkLatencyDistribution.of(10.0, 20.0, 30.0, 40.0);

        assertEquals(20.0, distribution.p50Ms(), 1e-9);
        assertEquals(40.0, distribution.p95Ms(), 1e-9, "nearest-rank 的 P95 取排序后第 4 个样本");
        assertEquals(4, distribution.sampleCount());
    }

    @Test
    void nearestRankOnHundredSamples() {
        List<Double> samples = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            samples.add((double) i);
        }

        NetworkLatencyDistribution distribution = NetworkLatencyDistribution.of(samples);

        assertEquals(50.0, distribution.p50Ms(), 1e-9);
        assertEquals(95.0, distribution.p95Ms(), 1e-9);
    }

    @Test
    void singleAndEmptySamples() {
        NetworkLatencyDistribution single = NetworkLatencyDistribution.of(120.0);
        assertEquals(120.0, single.p50Ms(), 1e-9);
        assertEquals(120.0, single.p95Ms(), 1e-9);

        NetworkLatencyDistribution empty = NetworkLatencyDistribution.of(List.of());
        assertFalse(empty.measured());
        assertEquals(0, empty.sampleCount());
    }

    @Test
    void latencyGateIsStrictAtBothBoundaries() {
        LatencyGate gate = new LatencyGate();

        assertTrue(gate.evaluate(new NetworkLatencyDistribution(49.9, 149.9, 100)).passed(),
                "49.9ms / 149.9ms 通过");

        var p50Breach = gate.evaluate(new NetworkLatencyDistribution(50.0, 149.9, 100));
        assertFalse(p50Breach.passed(), "50.0ms 不算小于 50ms");
        assertEquals(List.of("network_latency_p50"), p50Breach.failedMetrics());

        var p95Breach = gate.evaluate(new NetworkLatencyDistribution(49.9, 150.0, 100));
        assertFalse(p95Breach.passed(), "150.0ms 不算小于 150ms");
        assertEquals(List.of("network_latency_p95"), p95Breach.failedMetrics());

        var both = gate.evaluate(new NetworkLatencyDistribution(50.0, 150.0, 100));
        assertFalse(both.passed());
        assertEquals(2, both.violations().size());
    }

    @Test
    void unmeasuredLatencyPassesInsteadOfGuessing() {
        assertTrue(new LatencyGate().evaluate(NetworkLatencyDistribution.empty()).passed());
        assertTrue(new LatencyGate().evaluate(List.of()).passed());
    }

    @Test
    void windowEvaluationUsesWorstWindow() {
        MetricSample healthy = new MetricSample(Instant.EPOCH, 20.0, 12.0, 512, 4096, 4.0, 25.0, 30.0,
                10, 400, 3200.0, 2400, 20.0, 95.0);
        MetricSample spiky = new MetricSample(Instant.EPOCH, 20.0, 12.0, 512, 4096, 4.0, 25.0, 30.0,
                10, 400, 3200.0, 2400, 55.0, 160.0);
        MetricSample unmeasured = new MetricSample(Instant.EPOCH, 20.0, 12.0, 512, 4096, 4.0, 25.0, 30.0,
                10, 400);

        var result = new LatencyGate().evaluate(List.of(healthy, spiky, unmeasured));

        assertFalse(result.passed(), "一个尖峰窗口足以触发门禁");
        assertEquals(2, result.violations().size());
    }
}
