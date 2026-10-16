package io.laplace.sprite.cli;

import io.laplace.sprite.crashdiag.CrashArtifacts;
import io.laplace.sprite.crashdiag.CrashCollector;
import io.laplace.sprite.crashdiag.CrashDiagnosis;
import io.laplace.sprite.crashdiag.CrashDiagnoser;
import io.laplace.sprite.crashdiag.DiagnosisRenderer;
import io.laplace.sprite.crashdiag.HeapGcSummary;
import io.laplace.sprite.crashdiag.ScenePackageExporter;
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
import java.util.concurrent.Callable;

/**
 * {@code laplace crash}：15.5 崩溃诊断器。
 *
 * <p>从一个实例目录采集现场，按四类特征签名路由，做栈帧 × mod 类索引归因，输出结构化 JSON、
 * 中文 Markdown 与「问题现场包」。</p>
 */
@Command(name = "crash",
        description = "崩溃诊断器：现场采集、四类分类路由、栈帧归因、问题现场包导出",
        mixinStandardHelpOptions = true)
final class CrashCommand implements Callable<Integer> {

    @Option(names = "--instance", defaultValue = "paper-1", description = "实例 id")
    String instanceId;

    @Option(names = "--instance-dir", required = true,
            description = "实例目录（应包含 crash-reports/ logs/latest.log hs_err_pid*.log 等）")
    Path instanceDir;

    @Option(names = "--heap-used", defaultValue = "0", description = "OOM 联动：堆占用 MB（0 表示不提供）")
    long heapUsedMb;

    @Option(names = "--heap-max", defaultValue = "0", description = "OOM 联动：堆上限 MB")
    long heapMaxMb;

    @Option(names = "--out", description = "产物目录，默认 <workspace>/crash")
    Path out;

    @Option(names = "--workspace", description = "工作目录，默认 build/laplace")
    Path workspace;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        if (!Files.isDirectory(instanceDir)) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "实例目录不存在: " + instanceDir.toAbsolutePath());
        }
        Path dir = out != null ? out : Support.workspace(workspace).resolve("crash");
        var writer = spec.commandLine().getOut();

        CrashCollector collector = new CrashCollector();
        CrashArtifacts artifacts = collector.collect(instanceDir);
        String text = collector.combinedText(artifacts);
        HeapGcSummary heap = heapUsedMb > 0 ? new HeapGcSummary(heapUsedMb, heapMaxMb, null, null) : null;
        CrashDiagnosis diagnosis = new CrashDiagnoser().diagnose(instanceId, artifacts, text, heap);
        DiagnosisRenderer renderer = new DiagnosisRenderer(diagnosis);

        writer.println("崩溃诊断器（15.5） · 实例 " + instanceId);
        writer.println("  采集到 " + artifacts.found().size() + " 个现场文件（hs_err 存在="
                + artifacts.hsErrPresent() + "）");
        writer.println("  分类 " + diagnosis.category() + "（" + diagnosis.category().label()
                + "）签名 " + diagnosis.signature());
        writer.println("  类索引覆盖率 " + String.format("%.2f", diagnosis.classIndexCoverage())
                + (diagnosis.lowCoverage() ? "（覆盖率偏低，置信度已下调）" : ""));
        if (diagnosis.suspects().isEmpty()) {
            writer.println("  嫌疑 mod：无（栈帧未能归因）");
        } else {
            diagnosis.suspects().forEach(s -> writer.printf("    · %s 命中 %d 帧 置信度 %.2f%n",
                    s.modId(), s.frameHits(), s.confidence()));
        }
        writer.println("  人话建议：");
        renderer.toHumanAdvice().forEach(advice -> writer.println("    - " + advice));

        write(dir.resolve("crash-diagnosis.json"), renderer.toJson());
        write(dir.resolve("crash-diagnosis.md"), renderer.toMarkdown());
        Path sceneZip = new ScenePackageExporter().export(dir.resolve("scene-package.zip"), artifacts,
                diagnosis);
        writer.println("  诊断 JSON " + dir.resolve("crash-diagnosis.json").toAbsolutePath());
        writer.println("  诊断 Markdown " + dir.resolve("crash-diagnosis.md").toAbsolutePath());
        writer.println("  问题现场包 " + sceneZip.toAbsolutePath());
        return 0;
    }

    private static void write(Path target, String content) {
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
