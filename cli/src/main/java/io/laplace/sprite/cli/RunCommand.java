package io.laplace.sprite.cli;

import io.laplace.sprite.adapters.AdapterRegistryFactory;
import io.laplace.sprite.adapters.sim.SimAdapter;
import io.laplace.sprite.core.engine.Orchestrator;
import io.laplace.sprite.core.engine.RunRequest;
import io.laplace.sprite.core.engine.Runner;
import io.laplace.sprite.core.engine.SimulatedSession;
import io.laplace.sprite.core.engine.ThresholdGate;
import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.core.model.CheatTierName;
import io.laplace.sprite.core.model.FlagEvent;
import io.laplace.sprite.core.model.GateResult;
import io.laplace.sprite.core.model.NetworkModel;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.model.Scenario;
import io.laplace.sprite.core.model.TestRun;
import io.laplace.sprite.core.model.Thresholds;
import io.laplace.sprite.core.model.Violation;
import io.laplace.sprite.report.EvidenceVault;
import io.laplace.sprite.report.RegressionLibrary;
import io.laplace.sprite.report.ReportGenerator;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * {@code laplace run}：跑一次基准，产出报告、证据与回归入库，并按门禁返回退出码。
 */
@Command(name = "run", description = "运行基准（合法模拟 / 作弊矩阵 / 边界样本），产出报告与证据",
        mixinStandardHelpOptions = true)
final class RunCommand implements Callable<Integer> {

    @Option(names = "--platform", defaultValue = "in-process",
            description = "目标平台；in-process 表示进程内仿真轨")
    String platform;

    @Option(names = "--mc", defaultValue = Support.DEFAULT_MC, description = "Minecraft 版本")
    String mc;

    @Option(names = "--adapter", description = "加载器版本（模组线，如 0.19.5 / 21.11）")
    String loader;

    @Option(names = "--suite", defaultValue = "mixed", description = "legit | cheat | boundary | mixed")
    String suite;

    @Option(names = "--scenario", defaultValue = "pvp", description = "场景剧本 id")
    String scenarioId;

    @Option(names = "--target", defaultValue = "detection-engine", description = "检测目标 id")
    String targetId;

    @Option(names = "--players", defaultValue = "200", description = "模拟合法玩家数（legit / mixed）")
    int players;

    @Option(names = "--ticks", defaultValue = "400", description = "每条轨迹的 tick 数")
    int ticks;

    @Option(names = "--seed", defaultValue = "20261013", description = "随机种子（可复现）")
    long seed;

    @Option(names = "--tier", defaultValue = "DEFAULT", description = "作弊档位 min|default|max|hidden")
    CheatTierName tier;

    @Option(names = "--cheat", description = "仅运行指定作弊样本 id，可重复")
    List<String> cheatIds;

    @Option(names = "--parallel", description = "并发灌入（并发安全断言）")
    boolean parallel;

    @Option(names = "--coverage", defaultValue = "0", description = "核心模块行覆盖率（JaCoCo 回填）")
    double coverage;

    @Option(names = "--workspace", description = "工作目录，默认 build/laplace")
    Path workspace;

    @Option(names = "--run-id", description = "指定运行 id")
    String runId;

    @Option(names = "--json", description = "以 JSON 输出运行结果")
    boolean json;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        SuiteFactory factory = new SuiteFactory();
        if (!factory.scenarioExists(scenarioId)) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "未知场景剧本: " + scenarioId + "（可选: " + String.join(", ", factory.scenarioIds()) + "）");
        }
        Scenario scenario = factory.scenario(scenarioId);
        PlatformKey key = Support.resolvePlatform(spec, platform, mc, loader);
        NetworkModel network = factory.network(scenario, seed);
        EventStream baseline = factory.baseline(scenario, key, seed, ticks);

        List<SimulatedSession> sessions = new ArrayList<>();
        switch (suite.toLowerCase(java.util.Locale.ROOT)) {
            case "legit" -> sessions.addAll(factory.legit(players, scenario, key, seed, ticks));
            case "cheat" -> sessions.addAll(factory.cheat(factory.specs(cheatIds), tier, scenario, key,
                    network, seed, baseline));
            case "boundary" -> sessions.addAll(factory.boundary(factory.specs(cheatIds), scenario, key,
                    network, seed, baseline));
            case "mixed", "all" -> {
                sessions.addAll(factory.legit(players, scenario, key, seed, ticks));
                sessions.addAll(factory.cheat(factory.specs(cheatIds), tier, scenario, key, network,
                        seed, baseline));
            }
            default -> throw new CommandLine.ParameterException(spec.commandLine(),
                    "未知套件: " + suite + "（可选: legit, cheat, boundary, mixed）");
        }

        String id = runId != null && !runId.isBlank() ? runId
                : "run-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
        Thresholds thresholds = Thresholds.defaults();
        RunRequest request = new RunRequest(id, targetId, suite.toLowerCase(java.util.Locale.ROOT), key,
                seed, thresholds, parallel, coverage);

        Orchestrator orchestrator = new Orchestrator(new Runner(), AdapterRegistryFactory.defaultRegistry());
        TestRun run = orchestrator.runSuite(request, scenario, sessions, new SimAdapter());

        Path ws = Support.workspace(workspace);
        Path reportPath = ws.resolve("report").resolve("report.json");
        List<TestRun> accumulated = new ArrayList<>(readAccumulated(reportPath));
        accumulated.add(run);
        Path reportDir = new ReportGenerator().write(ws.resolve("report"), accumulated);

        EvidenceVault vault = new EvidenceVault(ws.resolve("evidence"));
        vault.archive(run.id(), "run", Json.write(run), "运行记录");
        run.flags().forEach(flag -> vault.archive(run.id(), "flag", flag.toString(),
                flag.falsePositive() ? "误报明细" : "检出明细"));

        RegressionLibrary library = new RegressionLibrary(ws.resolve("regression.json"));
        for (FlagEvent fp : run.falsePositives()) {
            if (fp.trajectoryRef() != null) {
                library.add(fp, fp.trajectoryRef(), "cli-run",
                        "run " + run.id() + " 误报自动入库（4.6 口径）");
            }
        }

        GateResult gate = new ThresholdGate().evaluate(run.metrics(), thresholds);
        if (json) {
            spec.commandLine().getOut().println(Json.write(run));
        } else {
            printSummary(run, gate, reportDir, vault.size(), library.size());
        }
        return gate.passed() ? 0 : 1;
    }

    /** 门禁按「一次完整基准」口径评估，因此报告目录累积历次运行而不是每次覆盖。 */
    private static List<TestRun> readAccumulated(Path reportPath) {
        if (!java.nio.file.Files.isRegularFile(reportPath)) {
            return List.of();
        }
        return GateCommand.readRuns(reportPath);
    }

    private void printSummary(TestRun run, GateResult gate, Path reportDir, int evidence, int regressions) {
        var out = spec.commandLine().getOut();
        out.printf("运行 %s（目标 %s / 套件 %s / 平台 %s）%n", run.id(), run.target(), run.suite(),
                run.platform() == null ? "in-process" : run.platform().id());
        out.printf("  合法样本 %d · 误报 %d · 误报率 %.6f%%%n", run.metrics().legitPlayers(),
                run.metrics().falsePositives(), run.metrics().falsePositiveRate() * 100);
        out.printf("  作弊样本 %d · 检出 %d · 检出率 %.4f%%%n", run.metrics().cheatSamples(),
                run.metrics().detectedSamples(), run.metrics().detectionRate() * 100);
        out.printf("  边界样本 %d · 符合预期 %d%n", run.metrics().boundarySamples(),
                run.metrics().boundaryPassed());
        out.printf("  状态 %s · 耗时 %d ms · 证据 %d 条 · 回归库 %d 条%n", run.status(),
                run.durationMs(), evidence, regressions);
        out.printf("  报告目录 %s%n", reportDir.toAbsolutePath());
        if (gate.passed()) {
            out.println("  门禁：通过");
        } else {
            out.println("  门禁：未通过");
            for (Violation v : gate.violations()) {
                out.println("    - " + v.message());
            }
        }
    }
}
