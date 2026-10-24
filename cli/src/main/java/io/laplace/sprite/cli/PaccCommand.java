package io.laplace.sprite.cli;

import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.core.model.TestRun;
import io.laplace.sprite.report.DetectionTrend;
import io.laplace.sprite.report.PaccRegressionSync;
import io.laplace.sprite.report.TrendHistory;
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
import java.util.Locale;
import java.util.concurrent.Callable;

/**
 * {@code laplace pacc}：PACC 夜间基准（升级实施文档 L-10）。
 *
 * <p>读取每个版本的合法 / 作弊样本计数，输出误报率与检出率趋势 SVG；
 * 不执行检测引擎，也不伪造采样。</p>
 */
@Command(name = "pacc", description = "PACC 夜间基准：误报 / 检出趋势图",
        mixinStandardHelpOptions = true)
final class PaccCommand implements Callable<Integer> {

    @Option(names = "--baseline", description = "夜间基准 JSON（BaselineRun 数组）")
    Path baseline;

    @Option(names = "--report", description = "test run 的 report.json；与 --version 搭配从运行结果汇总")
    Path report;

    @Option(names = "--version", description = "被测版本（配合 --report）")
    String version;

    @Option(names = "--out", required = true, description = "趋势图 SVG 输出路径")
    Path out;

    @Option(names = "--markdown", description = "可选：同时写一份 Markdown 表")
    Path markdown;

    @Option(names = "--history", description = "趋势历史 JSON；提供后追加本次结果并画完整历史")
    Path history;

    @Option(names = "--fail-on-regression", description = "相对上一条记录出现回归时返回非 0")
    boolean failOnRegression;

    @Option(names = "--sync-regression", description = "把误报用例同步到该工作区的回归库（需 --report）")
    Path regressionWorkspace;

    @Option(names = "--width", defaultValue = "720", description = "SVG 宽度")
    int width;

    @Option(names = "--height", defaultValue = "260", description = "SVG 高度")
    int height;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        if (baseline == null && (report == null || version == null)) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "需要 --baseline <json>，或 --report <report.json> + --version <版本>");
        }
        if (regressionWorkspace != null && report == null) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "--sync-regression 需要 --report 提供误报明细");
        }
        List<TestRun> reportRuns = report == null ? List.of() : GateCommand.readRuns(report);
        DetectionTrend current = DetectionTrend.from(baseline != null ? readBaseline() : aggregateReport(reportRuns));
        TrendHistory store = history == null ? null : new TrendHistory(history);
        if (store != null) {
            current.points().forEach(store::append);
            store.save();
        }
        DetectionTrend output = store == null ? current : store.trend();
        output.writeSvg(out, width, height);
        if (markdown != null) {
            writeMarkdown(output, markdown);
        }

        var writer = spec.commandLine().getOut();
        writer.println("PACC 夜间基准趋势");
        if (regressionWorkspace != null) {
            PaccRegressionSync.Summary sync = new PaccRegressionSync(regressionWorkspace).sync(reportRuns);
            writer.printf("  回归同步：误报 %d · 新增用例 %d · 缺轨迹 %d%n",
                    sync.falsePositives(), sync.addedCases(), sync.skippedWithoutTrajectory());
        }
        output.points().forEach(point -> writer.printf(Locale.ROOT, "  %s — 误报率 %.4f%% · 检出率 %.2f%%%n",
                point.version(), point.falsePositiveRate() * 100, point.detectionRate() * 100));
        if (store != null) {
            TrendHistory.Regression regression = store.compareToPrevious(TrendHistory.Thresholds.defaults());
            writer.printf("  历史 %d 条 · 回归判定 %s%n", store.entries().size(),
                    regression.regressed() ? "命中" : "未命中");
            regression.reasons().forEach(reason -> writer.println("  - " + reason));
            writer.printf("  趋势图 %s · 历史 %s%n", out.toAbsolutePath(), history.toAbsolutePath());
            if (regression.regressed() && failOnRegression) {
                return 1;
            }
        } else {
            writer.printf("  趋势图 %s%n", out.toAbsolutePath());
        }
        return 0;
    }

    private List<DetectionTrend.BaselineRun> readBaseline() {
        if (!Files.isRegularFile(baseline)) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "基准文件不存在: " + baseline.toAbsolutePath());
        }
        try {
            String content = Files.readString(baseline, StandardCharsets.UTF_8);
            return List.of(Json.jsonMapper().readValue(content, DetectionTrend.BaselineRun[].class));
        } catch (IOException ex) {
            throw new UncheckedIOException("解析夜间基准失败: " + baseline, ex);
        }
    }

    /** 把同一工作区累积的多条 TestRun 汇总成一条基线。 */
    private List<DetectionTrend.BaselineRun> aggregateReport(List<TestRun> runs) {
        int legit = runs.stream().mapToInt(run -> run.metrics().legitPlayers()).sum();
        int falsePositives = runs.stream().mapToInt(run -> run.metrics().falsePositives()).sum();
        int cheats = runs.stream().mapToInt(run -> run.metrics().cheatSamples()).sum();
        int detected = runs.stream().mapToInt(run -> run.metrics().detectedSamples()).sum();
        return List.of(new DetectionTrend.BaselineRun(version, legit, falsePositives, cheats, detected));
    }

    private static void writeMarkdown(DetectionTrend trend, Path file) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, trend.toMarkdown(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("写入趋势 Markdown 失败: " + file, ex);
        }
    }
}
