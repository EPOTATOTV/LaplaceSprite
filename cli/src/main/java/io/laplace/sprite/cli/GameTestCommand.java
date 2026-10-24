package io.laplace.sprite.cli;

import io.laplace.sprite.adapters.gametest.GameTestLoader;
import io.laplace.sprite.adapters.gametest.GameTestRunner;
import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.core.model.TestTrackReport;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * {@code laplace gametest}：四个加载器的官方 GameTest 执行入口（升级实施文档 L-02）。
 *
 * <p>真正调用项目里的 Gradle 包装脚本，任务名由 {@link GameTestLoader} 决定；
 * 日志解析失败、超时、脚本缺失都会如实返回失败。</p>
 */
@Command(name = "gametest", description = "GameTest 执行：Fabric / Quilt / Forge / NeoForge",
        mixinStandardHelpOptions = true)
final class GameTestCommand implements Callable<Integer> {

    @Option(names = "--loader", required = true, description = "FABRIC | QUILT | FORGE | NEOFORGE")
    GameTestLoader loader;

    @Option(names = "--project", defaultValue = ".", description = "模组项目目录（含 gradlew）")
    Path project;

    @Option(names = "--timeout-minutes", defaultValue = "20", description = "单次任务超时")
    long timeoutMinutes;

    @Option(names = "--mc", defaultValue = "26.3", description = "目标 Minecraft 版本；须走 1.21.5+ 的 GameTest 入口")
    String mcVersion;

    @Option(names = "--report", description = "可选：与 datapack 轨共用的 JSON 报告路径")
    Path reportFile;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        if (!Files.isDirectory(project)) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "项目目录不存在: " + project.toAbsolutePath());
        }
        if (!GameTestLoader.supportsMinecraft(mcVersion)) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "该版本没有可用的 GameTest 入口: " + mcVersion);
        }
        var writer = spec.commandLine().getOut();
        writer.printf("GameTest · %s · MC %s · %s%n", loader, mcVersion, project.toAbsolutePath());
        GameTestRunner.Report report = new GameTestRunner().run(loader, project,
                Duration.ofMinutes(timeoutMinutes));
        writer.printf("  命令 %s%n", String.join(" ", report.command()));
        writer.printf("  退出码 %d · 用例 %d · 失败 %d · 耗时 %dms%n", report.exitCode(),
                report.total(), report.failed(), report.durationMs());
        if (report.timedOut()) {
            writer.println("  超时：进程已被终止");
        }
        if (report.error() != null) {
            writer.println("  错误：" + report.error());
        }
        writer.println("  结果：" + (report.passed() ? "通过" : "未通过"));
        if (reportFile != null) {
            Json.writeFile(reportFile, TestTrackReport.gameTest(mcVersion, loader.name(),
                    report.passed(), report.total(), report.failed(), issues(report)));
        }
        return report.passed() ? 0 : 1;
    }

    private static List<String> issues(GameTestRunner.Report report) {
        List<String> issues = new ArrayList<>();
        if (report.error() != null) {
            issues.add(report.error());
        }
        if (report.timedOut()) {
            issues.add("GameTest 任务超时");
        }
        if (report.exitCode() != 0) {
            issues.add("Gradle 退出码 " + report.exitCode());
        }
        if (report.failed() > 0) {
            issues.add("失败用例 " + report.failed());
        }
        if (report.total() == 0) {
            issues.add("日志未解析到 GameTest 用例");
        }
        return issues;
    }
}
