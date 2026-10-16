package io.laplace.sprite.core.engine;

import io.laplace.sprite.core.model.GateResult;
import io.laplace.sprite.core.model.MetricSet;
import io.laplace.sprite.core.model.Thresholds;
import io.laplace.sprite.core.model.Violation;

import java.util.ArrayList;
import java.util.List;

/**
 * 门禁判定（4.5 判定层 ThresholdGate）：合法误报率 ≤0.01%、检出率 ≥95%、边界样本全通过。
 */
public final class ThresholdGate {

    public GateResult evaluate(MetricSet metrics, Thresholds thresholds) {
        List<Violation> violations = new ArrayList<>();

        double fpr = metrics.falsePositiveRate();
        if (fpr > thresholds.maxFalsePositiveRate()) {
            violations.add(Violation.above("false_positive_rate", fpr, thresholds.maxFalsePositiveRate()));
        }

        if (metrics.cheatSamples() > 0) {
            double rate = metrics.detectionRate();
            if (rate < thresholds.minDetectionRate()) {
                violations.add(Violation.below("detection_rate", rate, thresholds.minDetectionRate()));
            }
        }

        if (metrics.coverage() > 0 && metrics.coverage() < thresholds.minCoverage()) {
            violations.add(Violation.below("coverage", metrics.coverage(), thresholds.minCoverage()));
        }

        if (thresholds.requireBoundary() && metrics.boundarySamples() > 0
                && metrics.boundaryPassed() < metrics.boundarySamples()) {
            violations.add(Violation.failed("boundary",
                    "阈值边界样本 %d/%d 符合预期".formatted(metrics.boundaryPassed(), metrics.boundarySamples())));
        }

        if (thresholds.requireConcurrency() && !metrics.concurrencyOk()) {
            violations.add(Violation.failed("concurrency", "并发灌入下出现异常或状态丢失"));
        }

        return violations.isEmpty() ? GateResult.pass() : GateResult.fail(violations);
    }
}
