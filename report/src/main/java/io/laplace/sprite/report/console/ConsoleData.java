package io.laplace.sprite.report.console;

import io.laplace.sprite.core.model.CheatTierName;
import io.laplace.sprite.core.model.GateResult;
import io.laplace.sprite.core.model.LiveViewGrid;
import io.laplace.sprite.core.model.LogEntry;
import io.laplace.sprite.core.model.MetricHistory;
import io.laplace.sprite.core.model.ModuleHealth;
import io.laplace.sprite.core.model.NightlyBenchmarkResult;
import io.laplace.sprite.core.model.ServerPluginConfig;

import java.util.List;
import java.util.Map;

/**
 * 前端控制台数据契约（11.2 / 11.3 / 11.6 / 11.7）。
 *
 * <p>字段名即前端消费的 JSON 键，保持稳定。</p>
 */
public record ConsoleData(
        MetricCards cards,
        List<TestRunRow> runs,
        Map<String, Map<CheatTierName, Double>> detectionByCheckTier,
        Map<CheatTierName, Double> detectionByTier,
        MetricHistory falsePositiveHistory,
        MetricHistory detectionHistory,
        List<NightlyBenchmarkResult> nightly,
        GateResult gate,
        List<ModuleHealth> modules,
        ServerPluginConfig pluginConfig,
        int cheatSpecCount,
        int fingerprintCount,
        LiveViewGrid liveView,
        List<LogEntry> logs) {
}
