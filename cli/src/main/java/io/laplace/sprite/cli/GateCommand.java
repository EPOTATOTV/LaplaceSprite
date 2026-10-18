package io.laplace.sprite.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import io.laplace.sprite.core.engine.MetricAggregator;
import io.laplace.sprite.core.engine.ThresholdGate;
import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.core.model.FlagEvent;
import io.laplace.sprite.core.model.GateResult;
import io.laplace.sprite.core.model.MetricSet;
import io.laplace.sprite.core.model.RegressionCase;
import io.laplace.sprite.core.model.TestRun;
import io.laplace.sprite.core.model.Thresholds;
import io.laplace.sprite.core.model.Violation;
import io.laplace.sprite.report.RegressionHealth;
import io.laplace.sprite.report.RegressionLedger;
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
import java.util.List;
import java.util.concurrent.Callable;

/**
 * {@code laplace gate}：对已有运行结果判门禁（4.5 判定层）。未通过返回退出码 1，供 CI 使用。
 */
@Command(name = "gate", description = "判定门禁：误报率 / 检出率 / 边界样本 / 覆盖率 / 并发安全",
        mixinStandardHelpOptions = true)
final class GateCommand implements Callable<Integer> {

    static final double DEFAULT_MAX_FPR = 0.0001;
    static final double DEFAULT_MIN_DETECTION = 0.95;
    static final double DEFAULT_MIN_COVERAGE = 0.70;

    @Option(names = "--report", description = "report.json 路径；缺省取 <workspace>/report/report.json")
    Path report;

    @Option(names = "--workspace", description = "工作目录，默认 build/laplace")
    Path workspace;

    @Option(names = "--max-fpr", defaultValue = "0.0001", description = "合法误报率上限（默认 0.01%%）")
    double maxFalsePositiveRate;

    @Option(names = "--min-detection", defaultValue = "0.95", description = "作弊检出率下限（默认 0.95）")
    double minDetectionRate;

    @Option(names = "--min-coverage", defaultValue = "0.70", description = "覆盖率下限（默认 0.70）")
    double minCoverage;

    @Option(names = "--allow-concurrency-failure", description = "不要求并发安全全通过")
    boolean allowConcurrencyFailure;

    @Option(names = "--allow-boundary-failure", description = "不要求边界样本全通过")
    boolean allowBoundaryFailure;

    @Option(names = "--regression", description = "回归库 JSON（RegressionCase 数组）")
    Path regression;

    @Option(names = "--regression-flags", description = "误报 flag JSON（FlagEvent 数组），用于算复现覆盖率")
    Path regressionFlags;

    @Option(names = "--bypass-reports", description = "绕过报告 JSON（BypassReport 数组）")
    Path bypassReports;

    @Option(names = "--require-regression-health", description = "回归库健康度不达标时门禁失败")
    boolean requireRegressionHealth;

    @Option(names = "--min-regression-cases", defaultValue = "0",
            description = "回归用例规模下限（默认只上报不拦截）")
    int minRegressionCases;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        if (requireRegressionHealth && !Files.isRegularFile(regressionFile())) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "--require-regression-health 需要回归台账或 --regression 文件");
        }
        Path source = report != null ? report
                : Support.workspace(workspace).resolve("report").resolve("report.json");
        List<TestRun> runs = readRuns(source);
        Thresholds thresholds = new Thresholds(maxFalsePositiveRate, minDetectionRate, minCoverage,
                !allowConcurrencyFailure, !allowBoundaryFailure);

        MetricSet total = MetricAggregator.sum(runs);
        GateResult gate = new ThresholdGate().evaluate(total, thresholds);

        var out = spec.commandLine().getOut();
        out.printf("门禁输入：%s（%d 次运行）%n", source.toAbsolutePath(), runs.size());
        out.printf("  合法样本 %d · 误报 %d · 误报率 %.6f%%（上限 %.6f%%）%n", total.legitPlayers(),
                total.falsePositives(), total.falsePositiveRate() * 100, maxFalsePositiveRate * 100);
        out.printf("  作弊样本 %d · 检出 %d · 检出率 %.4f%%（下限 %.4f%%）%n", total.cheatSamples(),
                total.detectedSamples(), total.detectionRate() * 100, minDetectionRate * 100);
        out.printf("  边界样本 %d/%d · 覆盖率 %.2f%% · 并发安全 %s%n", total.boundaryPassed(),
                total.boundarySamples(), total.coverage() * 100, total.concurrencyOk() ? "通过" : "未通过");
        out.printf("  功能冒烟 %d/%d（%.2f%%）%n", total.functionalSmokePassed(),
                total.functionalSmokeTotal(), total.functionalSmokePassRate() * 100);
        out.printf("  性能基线 %s（Δ %.2f%%）· 资源与装配 %s%n",
                total.performanceBaselineOk() ? "未劣化" : "劣化",
                total.performanceBaselineDelta() * 100,
                total.resourceAndAssemblyOk() ? "通过" : "未通过");

        boolean regressionOk = true;
        if (regression != null || Files.isRegularFile(regressionFile())) {
            RegressionHealth.Health health = evaluateRegression();
            out.printf("  回归库 %d 条 · 绕过报告 %d 条（已转化 %d，%.2f%%）· 误报复现 %d/%d（%.2f%%）%n",
                    health.caseCount(), health.bypassReports(), health.convertedBypasses(),
                    health.bypassConversionRate() * 100, health.coveredFalsePositives(),
                    health.falsePositiveFlags(), health.falsePositiveCoverage() * 100);
            out.println("  回归库健康度：" + (health.healthy() ? "通过" : "未通过")
                    + (health.overdueBypasses() > 0 ? "（超期 " + health.overdueBypasses() + " 条）" : ""));
            health.violations().forEach(line -> out.println("    - " + line));
            // 自动发现台账只做展示；显式 --regression 或 --require-regression-health 才改变门禁结论。
            boolean enforce = regression != null || requireRegressionHealth;
            regressionOk = !enforce || health.healthy();
        }

        if (gate.passed() && regressionOk) {
            out.println("门禁结果：通过");
            return 0;
        }
        out.println("门禁结果：未通过");
        for (Violation v : gate.violations()) {
            out.println("  - [" + v.metric() + "] " + v.message());
        }
        if (!regressionOk) {
            out.println("  - [regression-health] 回归库健康度未通过");
        }
        return 1;
    }

    private RegressionHealth.Health evaluateRegression() {
        Path casesFile = regressionFile();
        Path parent = casesFile.toAbsolutePath().getParent();
        List<RegressionCase> cases = readArray(casesFile, RegressionCase[].class);
        Path flagsFile = regressionFlags != null ? regressionFlags
                : parent.resolve(RegressionLedger.FLAGS_FILE);
        Path bypassFile = bypassReports != null ? bypassReports
                : parent.resolve(RegressionLedger.BYPASS_FILE);
        List<FlagEvent> flags = Files.isRegularFile(flagsFile)
                ? readArray(flagsFile, FlagEvent[].class) : List.of();
        List<RegressionHealth.BypassReport> bypasses = Files.isRegularFile(bypassFile)
                ? readArray(bypassFile, RegressionHealth.BypassReport[].class) : List.of();
        RegressionHealth.Thresholds defaults = RegressionHealth.Thresholds.defaults();
        return RegressionHealth.evaluate(cases, flags, bypasses,
                new RegressionHealth.Thresholds(minRegressionCases, defaults.minBypassConversion(),
                        defaults.minFalsePositiveCoverage(), defaults.bypassWindow()),
                java.time.Instant.now());
    }

    /** 显式 --regression 优先，否则读工作区的回归台账。 */
    private Path regressionFile() {
        if (regression != null) {
            return regression;
        }
        return Support.workspace(workspace).resolve("regression").resolve(RegressionLedger.LIBRARY_FILE);
    }

    private static <T> List<T> readArray(Path file, Class<T[]> type) {
        if (file == null || !Files.isRegularFile(file)) {
            throw new CommandLine.ParameterException(new CommandLine(new Object()),
                    "找不到回归库文件: " + (file == null ? "(null)" : file.toAbsolutePath()));
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            return List.of(Json.jsonMapper().readValue(content, type));
        } catch (IOException ex) {
            throw new UncheckedIOException("解析回归库文件失败: " + file, ex);
        }
    }

    static List<TestRun> readRuns(Path source) {
        if (!Files.isRegularFile(source)) {
            throw new CommandLine.ParameterException(new CommandLine(new Object()),
                    "找不到运行结果文件: " + source.toAbsolutePath());
        }
        try {
            String content = Files.readString(source, StandardCharsets.UTF_8);
            return Json.jsonMapper().readValue(content, new TypeReference<List<TestRun>>() {
            });
        } catch (IOException ex) {
            throw new UncheckedIOException("解析运行结果失败: " + source, ex);
        }
    }
}
