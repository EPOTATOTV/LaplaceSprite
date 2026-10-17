package io.laplace.sprite.core.engine;

import io.laplace.sprite.core.model.DetectionBreakdown;
import io.laplace.sprite.core.model.GateResult;
import io.laplace.sprite.core.model.MetricSet;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.model.RunStatus;
import io.laplace.sprite.core.model.TestRun;
import io.laplace.sprite.core.model.Thresholds;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 4.5 功能类指标、门禁与聚合。 */
class MetricGateTest {

    private static final PlatformKey PAPER = PlatformKey.of(PlatformFamily.PAPER, "1.21.11");

    @Test
    void legacyMetricSetKeepsTheOldDetectionGatesPassing() {
        MetricSet legacy = new MetricSet(500, 0, 100, 96, 10, 10, true, 0.75);
        GateResult gate = new ThresholdGate().evaluate(legacy, Thresholds.defaults());

        assertTrue(gate.passed(), "旧构造不得被新增门禁拦下");
        assertEquals(1.0, legacy.functionalSmokePassRate());
        assertTrue(legacy.resourceAndAssemblyOk());
        assertTrue(legacy.performanceNotDegraded());
    }

    @Test
    void zeroSmokeSamplesAreNotApplicableInsteadOfFailure() {
        MetricSet noSmoke = new MetricSet(500, 0, 100, 96, 10, 10, true, 0.75, 0, 0,
                true, 0.0, true, false, true);
        GateResult gate = new ThresholdGate().evaluate(noSmoke, Thresholds.defaults());

        assertTrue(gate.passed());
        assertEquals(1.0, noSmoke.functionalSmokePassRate(), "0/0 按不适用处理");
        assertTrue(gate.violations().stream()
                .noneMatch(v -> v.metric().equals("functional_smoke_pass_rate")));
    }

    @Test
    void smokeGateRequiresAllPassed() {
        MetricSet nineOfTen = new MetricSet(0, 0, 0, 0, 0, 0, true, 0.0, 9, 10,
                true, 0.0, true, false, true);
        GateResult gate = new ThresholdGate().evaluate(nineOfTen, Thresholds.defaults());

        assertFalse(gate.passed());
        assertTrue(gate.violations().stream().anyMatch(v -> v.metric().equals("functional_smoke_pass_rate")
                && v.message().contains("功能冒烟")));
    }

    @Test
    void performanceBaselineFailsOnNegativeDeltaOrUnmeasuredBaseline() {
        MetricSet degraded = new MetricSet(0, 0, 0, 0, 0, 0, true, 0.0, 0, 0,
                true, -0.02, true, false, true);
        GateResult degradedGate = new ThresholdGate().evaluate(degraded, Thresholds.defaults());
        assertFalse(degradedGate.passed());
        assertTrue(degradedGate.violations().stream().anyMatch(
                v -> v.metric().equals("performance_baseline_delta") && v.message().contains("劣化")));

        MetricSet flat = new MetricSet(0, 0, 0, 0, 0, 0, true, 0.0, 0, 0,
                true, 0.0, true, false, true);
        assertTrue(new ThresholdGate().evaluate(flat, Thresholds.defaults()).passed(), "持平允许通过");

        MetricSet unmeasured = new MetricSet(0, 0, 0, 0, 0, 0, true, 0.0, 0, 0,
                false, 0.0, true, false, true);
        GateResult unmeasuredGate = new ThresholdGate().evaluate(unmeasured, Thresholds.defaults());
        assertFalse(unmeasuredGate.passed());
        assertTrue(unmeasuredGate.violations().stream()
                .anyMatch(v -> v.metric().equals("performance_baseline")));
    }

    @Test
    void resourceAndAssemblyReportsEachBrokenCondition() {
        MetricSet broken = new MetricSet(0, 0, 0, 0, 0, 0, true, 0.0, 0, 0,
                true, 0.0, false, true, false);
        GateResult gate = new ThresholdGate().evaluate(broken, Thresholds.defaults());

        assertFalse(gate.passed());
        assertFalse(broken.resourceAndAssemblyOk());
        String message = gate.violations().stream()
                .filter(v -> v.metric().equals("resource_assembly"))
                .findFirst().orElseThrow().message();
        assertTrue(message.contains("依赖解析存在缺失"), message);
        assertTrue(message.contains("版本矩阵出现降级告警"), message);
        assertTrue(message.contains("资源包 / 数据包加载失败"), message);
    }

    @Test
    void existingDetectionBoundariesStayStrictlyTheSame() {
        MetricSet atLimit = new MetricSet(10_000, 1, 100, 95, 5, 5, true, 0.70);
        assertTrue(new ThresholdGate().evaluate(atLimit, Thresholds.defaults()).passed(),
                "误报率 = 上限、检出率 = 下限、覆盖率 = 下限都应通过");

        MetricSet overFalsePositive = new MetricSet(10_000, 2, 100, 95, 5, 5, true, 0.70);
        assertFalse(new ThresholdGate().evaluate(overFalsePositive, Thresholds.defaults()).passed());
    }

    @Test
    void sumAggregatesCountsAsSumsAndFlagsAsAnd() {
        TestRun first = run("run-1", new MetricSet(100, 0, 50, 48, 4, 4, true, 0.80,
                3, 4, true, -0.01, true, false, true));
        TestRun second = run("run-2", new MetricSet(200, 0, 50, 47, 6, 6, false, 0.85,
                2, 2, false, -0.03, false, true, false));

        MetricSet total = MetricAggregator.sum(List.of(first, second));

        assertEquals(300, total.legitPlayers());
        assertEquals(100, total.cheatSamples());
        assertEquals(95, total.detectedSamples());
        assertEquals(10, total.boundarySamples());
        assertEquals(5, total.functionalSmokePassed());
        assertEquals(6, total.functionalSmokeTotal());
        assertEquals(0.85, total.coverage(), 1e-9, "覆盖率取最大值");
        assertFalse(total.concurrencyOk());
        assertFalse(total.performanceBaselineOk());
        assertEquals(-0.03, total.performanceBaselineDelta(), 1e-9, "性能变化取最差值");
        assertFalse(total.dependencyResolved());
        assertTrue(total.versionMatrixDegraded());
        assertFalse(total.resourcePackLoadOk());
        assertFalse(total.resourceAndAssemblyOk());
    }

    private static TestRun run(String id, MetricSet metrics) {
        return new TestRun(id, "detection-engine", "mixed", PAPER, metrics,
                DetectionBreakdown.empty(), List.of(), List.of(), RunStatus.PASSED, 7L,
                Instant.EPOCH, 10L);
    }
}
