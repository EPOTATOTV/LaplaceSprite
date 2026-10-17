package io.laplace.sprite.cli;

import io.laplace.sprite.cheats.AnomalyMatrix;
import io.laplace.sprite.core.engine.MetricAggregator;
import io.laplace.sprite.core.model.SampleTierName;
import io.laplace.sprite.core.model.LiveViewGrid;
import io.laplace.sprite.core.model.LogEntry;
import io.laplace.sprite.core.model.ModuleHealth;
import io.laplace.sprite.core.model.NightlyBenchmarkResult;
import io.laplace.sprite.core.model.RunStatus;
import io.laplace.sprite.core.model.ServerPluginConfig;
import io.laplace.sprite.core.model.TestRun;
import io.laplace.sprite.core.model.Thresholds;
import io.laplace.sprite.probes.ProcessScannerStub;
import io.laplace.sprite.report.EvidenceVault;
import io.laplace.sprite.report.LogStreamWriter;
import io.laplace.sprite.report.console.ConsoleData;
import io.laplace.sprite.report.console.ConsoleDataBuilder;
import io.laplace.sprite.servers.LiveViewService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * {@code laplace console}：把平台运行数据导出成控制台数据契约（11.2 / 11.3 / 11.6 / 11.7），
 * 供前端 {@code /api/console} 直接消费。
 */
@Command(name = "console", description = "导出 Laplace Console 数据（前端数据契约）",
        mixinStandardHelpOptions = true)
final class ConsoleCommand implements Callable<Integer> {

    @Option(names = "--workspace", description = "工作目录，默认 build/laplace")
    Path workspace;

    @Option(names = "--out", description = "输出路径，默认 <workspace>/console.json")
    Path out;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        Path ws = Support.workspace(workspace);
        List<TestRun> runs = readRuns(ws);
        ServerPluginConfig pluginConfig = ServerPluginConfig.safeDefaults();
        LiveViewGrid grid = new LiveViewService(Map.of()).grid();
        List<LogEntry> logs = new LogStreamWriter(ws.resolve("logs.log")).tail(200);
        List<ModuleHealth> modules = moduleHealth(ws, runs);
        Thresholds thresholds = Thresholds.defaults();
        int cheatSpecs = new AnomalyMatrix().totalConfigurations();
        int fingerprints = new ProcessScannerStub().fingerprintCount();

        ConsoleDataBuilder builder = new ConsoleDataBuilder();
        ConsoleData data = builder.build(runs, modules, pluginConfig, grid, logs,
                nightly(runs), thresholds, cheatSpecs, fingerprints);

        Path target = out != null ? out : ws.resolve("console.json");
        try {
            Files.createDirectories(target.toAbsolutePath().getParent());
            Files.writeString(target, builder.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("写出控制台数据失败: " + target, ex);
        }

        var writer = spec.commandLine().getOut();
        writer.printf("已导出控制台数据 %s%n", target.toAbsolutePath());
        writer.printf("  运行 %d 次 · 模拟人数 %d · TestSpec %d · 进程指纹 %d%n", data.cards().todayRuns(),
                data.cards().simulatedPlayers(), data.cards().cheatSpecCount(), fingerprints);
        writer.printf("  误报率 %.6f%% · 检出率 %.4f%% · 门禁 %s%n", data.cards().falsePositiveRate() * 100,
                data.cards().detectionRate() * 100, data.gate().passed() ? "通过" : "未通过");
        writer.printf("  检出矩阵维度：%d 个检测项 × %d 个档位%n", data.detectionByCheckTier().size(),
                SampleTierName.values().length);
        return 0;
    }

    private static List<TestRun> readRuns(Path workspace) {
        Path report = workspace.resolve("report").resolve("report.json");
        return Files.isRegularFile(report) ? GateCommand.readRuns(report) : List.of();
    }

    private static List<ModuleHealth> moduleHealth(Path workspace, List<TestRun> runs) {
        int checks = MetricAggregator.checkNames(runs).size();
        long flags = runs.stream().mapToLong(r -> r.flags().size()).sum();
        int evidence = new EvidenceVault(workspace.resolve("evidence")).size();
        int fingerprints = new ProcessScannerStub().fingerprintCount();

        List<ModuleHealth> modules = new ArrayList<>();
        modules.add(new ModuleHealth("检测引擎核心", "1.0.0", RunStatus.PASSED, Map.of("checks", (long) checks)));
        modules.add(new ModuleHealth("规则运行时", "1.0.0", RunStatus.PASSED, Map.of("flags", flags)));
        modules.add(new ModuleHealth("Addon Scanner", "1.0.0", RunStatus.PASSED, Map.of()));
        modules.add(new ModuleHealth("ProbeStub", "1.0.0", RunStatus.PASSED,
                Map.of("fingerprints", (long) fingerprints)));
        modules.add(new ModuleHealth("EvidenceVault", "1.0.0", RunStatus.PASSED,
                Map.of("records", (long) evidence)));
        modules.add(new ModuleHealth("跨平台更新器", "1.0.0", RunStatus.PASSED, Map.of()));
        modules.add(new ModuleHealth("启动器", "1.0.0", RunStatus.PASSED, Map.of()));
        return modules;
    }

    /** 按运行日期归成夜间基准（口径冻结：同一天的运行全部通过才算通过）。 */
    private static List<NightlyBenchmarkResult> nightly(List<TestRun> runs) {
        if (runs.isEmpty()) {
            return List.of();
        }
        ZoneId zone = ZoneId.systemDefault();
        Map<LocalDate, List<TestRun>> byDate = runs.stream().collect(Collectors.groupingBy(
                r -> LocalDate.ofInstant(r.startedAt(), zone), TreeMap::new, Collectors.toList()));
        Set<LocalDate> dates = byDate.keySet();
        List<NightlyBenchmarkResult> results = new ArrayList<>();
        for (Map.Entry<LocalDate, List<TestRun>> entry : byDate.entrySet()) {
            List<TestRun> dayRuns = entry.getValue();
            int failed = (int) dayRuns.stream().filter(r -> r.status() != RunStatus.PASSED).count();
            results.add(new NightlyBenchmarkResult(entry.getKey().atStartOfDay(zone).toInstant(),
                    failed == 0, dayRuns.stream().mapToLong(TestRun::durationMs).sum(), dayRuns.size(),
                    failed, "1.0.0", failed > 0 && dates.size() > 1));
        }
        return results;
    }
}
