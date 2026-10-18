package io.laplace.sprite.cli;

import io.laplace.sprite.adapters.gametest.GameTestLoader;
import io.laplace.sprite.adapters.gametest.GameTestRunner;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        if (!Files.isDirectory(project)) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "项目目录不存在: " + project.toAbsolutePath());
        }
        var writer = spec.commandLine().getOut();
        writer.printf("GameTest · %s · %s%n", loader, project.toAbsolutePath());
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
        return report.passed() ? 0 : 1;
    }
}
