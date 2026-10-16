package io.laplace.sprite.report.console;

import io.laplace.sprite.core.engine.MetricAggregator;
import io.laplace.sprite.core.engine.ThresholdGate;
import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.core.model.GateResult;
import io.laplace.sprite.core.model.LiveViewGrid;
import io.laplace.sprite.core.model.LogEntry;
import io.laplace.sprite.core.model.MetricSet;
import io.laplace.sprite.core.model.ModuleHealth;
import io.laplace.sprite.core.model.NightlyBenchmarkResult;
import io.laplace.sprite.core.model.ServerPluginConfig;
import io.laplace.sprite.core.model.TestRun;
import io.laplace.sprite.core.model.Thresholds;

import java.util.List;

/**
 * 控制台数据构建：由原始运行 / 模块 / 配置输入派生 {@link ConsoleData}，指标统一走 {@link MetricAggregator}。
 */
public final class ConsoleDataBuilder {

    public ConsoleData build(List<TestRun> runs, List<ModuleHealth> modules,
                             ServerPluginConfig pluginConfig, LiveViewGrid liveView, List<LogEntry> logs,
                             List<NightlyBenchmarkResult> nightly, Thresholds thresholds,
                             int cheatSpecCount, int fingerprintCount) {
        List<TestRun> safeRuns = runs == null ? List.of() : runs;
        MetricSet total = MetricAggregator.sum(safeRuns);
        GateResult gate = new ThresholdGate().evaluate(total, thresholds);
        MetricCards cards = new MetricCards(safeRuns.size(), total.legitPlayers(), cheatSpecCount,
                total.falsePositiveRate(), total.detectionRate(), gate.passed() ? 1 : 0);
        return new ConsoleData(
                cards,
                safeRuns.stream().map(ConsoleDataBuilder::row).toList(),
                MetricAggregator.detectionRateByCheck(safeRuns),
                MetricAggregator.detectionRateByTier(safeRuns),
                MetricAggregator.history("false_positive_rate", thresholds.maxFalsePositiveRate(), safeRuns),
                MetricAggregator.history("detection_rate", thresholds.minDetectionRate(), safeRuns),
                nightly == null ? List.of() : nightly,
                gate,
                modules == null ? List.of() : modules,
                pluginConfig,
                cheatSpecCount,
                fingerprintCount,
                liveView,
                logs == null ? List.of() : logs);
    }

    public String toJson(ConsoleData data) {
        return Json.write(data);
    }

    private static TestRunRow row(TestRun run) {
        MetricSet m = run.metrics();
        return new TestRunRow(run.id(), run.target(), run.suite(), m.legitPlayers(),
                m.falsePositiveRate(), m.detectionRate(), m.boundarySamples(), run.status(),
                run.platform(), run.startedAt(), run.durationMs());
    }
}
