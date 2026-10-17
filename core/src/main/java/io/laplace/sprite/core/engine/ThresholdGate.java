package io.laplace.sprite.core.engine;

import io.laplace.sprite.core.model.GateResult;
import io.laplace.sprite.core.model.MetricSet;
import io.laplace.sprite.core.model.Thresholds;
import io.laplace.sprite.core.model.Violation;

import java.util.ArrayList;
import java.util.List;

/**
 * 门禁判定（4.5 判定层）。
 *
 * <p>检测类四项（误报率 / 检出率 / 边界样本 / 覆盖率 / 并发安全）保持原有口径；功能类三项
 * （功能冒烟 / 性能基线 / 资源与装配）是 v1.3.0 新增维度，互不影响。</p>
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

        // 功能冒烟：total 为 0 表示本轮没有功能冒烟用例，按不适用跳过，不能当作 0/0 失败。
        if (metrics.functionalSmokeTotal() > 0) {
            double rate = metrics.functionalSmokePassRate();
            if (rate < thresholds.minFunctionalSmokePassRate()) {
                violations.add(new Violation("functional_smoke_pass_rate", ">=", rate,
                        thresholds.minFunctionalSmokePassRate(),
                        "功能冒烟通过率 %.4f%% 低于门禁 %.4f%%（%d/%d 通过）".formatted(
                                rate * 100, thresholds.minFunctionalSmokePassRate() * 100,
                                metrics.functionalSmokePassed(), metrics.functionalSmokeTotal())));
            }
        }

        if (thresholds.requirePerformanceBaseline()) {
            if (!metrics.performanceBaselineOk()) {
                violations.add(Violation.failed("performance_baseline",
                        "性能基线对比未完成，无法判定 TPS / MSPT / 帧率是否劣化"));
            }
            // delta 为相对 TPS 变化率，负值即劣化；0 表示与上一版本基线持平，允许通过。
            if (metrics.performanceBaselineDelta() < 0.0) {
                violations.add(new Violation("performance_baseline_delta", ">=",
                        metrics.performanceBaselineDelta(), 0.0,
                        "性能相对上一版本基线劣化 %.4f%%（TPS 相对变化）".formatted(
                                metrics.performanceBaselineDelta() * 100)));
            }
        }

        if (thresholds.requireResourceAssembly() && !metrics.resourceAndAssemblyOk()) {
            List<String> problems = new ArrayList<>();
            if (!metrics.dependencyResolved()) {
                problems.add("依赖解析存在缺失");
            }
            if (metrics.versionMatrixDegraded()) {
                problems.add("版本矩阵出现降级告警");
            }
            if (!metrics.resourcePackLoadOk()) {
                problems.add("资源包 / 数据包加载失败");
            }
            violations.add(Violation.failed("resource_assembly",
                    "资源与装配未全部通过：" + String.join("；", problems)));
        }

        return violations.isEmpty() ? GateResult.pass() : GateResult.fail(violations);
    }
}
