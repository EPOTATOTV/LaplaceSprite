package io.laplace.sprite.cli;

import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.debug.BreakpointLibrary;
import io.laplace.sprite.debug.BreakpointTemplate;
import io.laplace.sprite.debug.IdeConfigGenerator;
import io.laplace.sprite.debug.JdwpOptions;
import io.laplace.sprite.debug.LogConfigGenerator;
import io.laplace.sprite.debug.MixinExportCollector;
import io.laplace.sprite.debug.MixinExportOptions;
import io.laplace.sprite.debug.MixinExportSnapshot;
import io.laplace.sprite.debug.SourceMapGenerator;
import io.laplace.sprite.debug.SourceMapReport;
import io.laplace.sprite.debug.SourceMapRequest;
import io.laplace.sprite.hotreload.ChangeKind;
import io.laplace.sprite.hotreload.ColdStartLifecycle;
import io.laplace.sprite.hotreload.HotReloadCapability;
import io.laplace.sprite.hotreload.HotReloadMatrix;
import io.laplace.sprite.hotreload.HotReloadReport;
import io.laplace.sprite.hotreload.JvmKind;
import io.laplace.sprite.hotreload.JvmProbe;
import io.laplace.sprite.hotreload.ReloadMisuseDetector;
import io.laplace.sprite.hotreload.ResourceChangeRouter;
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
 * {@code laplace debug}：15.1 调试轨 + 15.2 热重载双轨制。
 *
 * <p>产出的是「可用的配置与判定」：jdwp 参数、源码映射状态、Mixin 织入快照、IDE / 日志配置、
 * 热重载能力判定与冻结矩阵。真正拉起调试实例依赖外部工程与 IDE，本命令不假装执行过。</p>
 */
@Command(name = "debug",
        description = "调试轨与热重载双轨制：jdwp 注入 / 源码映射 / Mixin 织入快照 / IDE 配置 / 热重载判定",
        mixinStandardHelpOptions = true)
final class DebugCommand implements Callable<Integer> {

    @Option(names = "--instance", defaultValue = "debug-1", description = "实例 id")
    String instanceId;

    @Option(names = "--platform", defaultValue = "paper", description = "目标平台")
    String platform;

    @Option(names = "--mc", defaultValue = Support.DEFAULT_MC, description = "Minecraft 版本")
    String mc;

    @Option(names = "--adapter", description = "加载器版本")
    String loader;

    @Option(names = "--artifact", defaultValue = "sprite-probe", description = "被测产物名")
    String artifactName;

    @Option(names = "--jdwp-port", defaultValue = "5005", description = "jdwp 端口")
    int jdwpPort;

    @Option(names = "--wait-ide", description = "等待 IDE 附加（suspend=y）；默认 suspend=n 以免 CI 阻塞")
    boolean waitForIde;

    @Option(names = "--project", description = "Loom 工程目录，用于生成可读反编译源码")
    Path project;

    @Option(names = "--modid", defaultValue = "sprite", description = "被测 modid（日志配置按它开 DEBUG）")
    String modid;

    @Option(names = "--change", defaultValue = "METHOD_BODY", description = "本次改动类型")
    ChangeKind change;

    @Option(names = "--jvm", description = "JVM 类型；缺省时探测当前环境")
    JvmKind jvm;

    @Option(names = "--log", description = "latest.log 路径，用于热重载禁忌检测")
    Path log;

    @Option(names = "--changed-path", description = "被改动的资源路径，用于资源变更路由表")
    String changedPath;

    @Option(names = "--out", description = "产物目录，默认 <workspace>/debug")
    Path out;

    @Option(names = "--workspace", description = "工作目录，默认 build/laplace")
    Path workspace;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        Path dir = out != null ? out : Support.workspace(workspace).resolve("debug");
        var writer = spec.commandLine().getOut();
        try {
            Files.createDirectories(dir);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }

        PlatformKey key = Support.requirePlatform(spec, platform, mc, loader);
        writer.println("调试轨（15.1）/ 热重载双轨制（15.2） · " + key.id() + " · 实例 " + instanceId);

        writeJdwp(writer, dir);
        writeSourceMap(writer, dir);
        writeMixinSnapshot(writer, dir);
        writeConfigs(writer, dir, key);
        writeBreakpoints(writer);
        writeHotReload(writer, dir);
        writeColdStart(writer);
        writeResourceRoute(writer);

        writer.println("产物目录 " + dir.toAbsolutePath());
        return 0;
    }

    private void writeJdwp(java.io.PrintWriter writer, Path dir) {
        JdwpOptions options = waitForIde ? JdwpOptions.waitForIde(jdwpPort) : JdwpOptions.ciDefault(jdwpPort);
        writer.println("  [jdwp] " + options.toAgentArg());
        writer.println("         端口映射回宿主机 IDE: " + options.publishArgument()
                + (options.waitsForIde() ? "（等待 IDE 附加，会阻塞启动）" : "（suspend=n，不阻塞 CI）"));
        write(dir.resolve("jdwp-args.txt"), options.toAgentArg());
    }

    private void writeSourceMap(java.io.PrintWriter writer, Path dir) {
        if (project == null) {
            writer.println("  [源码映射] 未提供 --project，跳过（没有源码映射，断点打不进 MC 类）");
            return;
        }
        SourceMapReport report = new SourceMapGenerator()
                .generate(SourceMapRequest.of(instanceId, project));
        writer.println("  [源码映射] 生成=" + report.generated() + " 目录=" + report.sourceDir()
                + " 生成器=" + report.generator());
        report.notes().forEach(note -> writer.println("           · " + note));
    }

    private void writeMixinSnapshot(java.io.PrintWriter writer, Path dir) {
        MixinExportOptions options = MixinExportOptions.devDefault();
        writer.println("  [Mixin] JVM 参数 " + String.join(" ", options.jvmArguments()));
        Path instanceRoot = dir.resolve("instance");
        MixinExportCollector collector = new MixinExportCollector(instanceRoot, options);
        MixinExportSnapshot snapshot = collector.snapshot(instanceId);
        if (snapshot.empty()) {
            writer.println("  [Mixin] " + options.exportPath(instanceRoot) + " 下暂无织入产物（需要先跑过调试实例）");
            return;
        }
        writer.println("  [Mixin] 织入类 " + snapshot.exportedClassCount()
                + " 个，反编译源码 " + snapshot.decompiledSourceCount() + " 个");
        Path zip = collector.bundleZip(instanceId, dir.resolve("mixin-" + instanceId + ".zip"));
        writer.println("  [Mixin] 已打包证据 " + zip.toAbsolutePath());
    }

    private void writeConfigs(java.io.PrintWriter writer, Path dir, PlatformKey key) {
        IdeConfigGenerator ide = new IdeConfigGenerator();
        JdwpOptions options = waitForIde ? JdwpOptions.waitForIde(jdwpPort) : JdwpOptions.ciDefault(jdwpPort);
        String configName = ide.configNameFor(key, artifactName);
        Path runConfig = dir.resolve(".run").resolve(configName + ".xml");
        write(runConfig, ide.remoteRunConfigFor(key, artifactName, options));
        writer.println("  [IDE] " + runConfig.toAbsolutePath());

        Path logConfig = dir.resolve("log4j-dev.xml");
        write(logConfig, new LogConfigGenerator().log4j2Xml(modid));
        writer.println("  [日志] " + logConfig.toAbsolutePath() + "（" + modid + " 开 DEBUG）");
    }

    private void writeBreakpoints(java.io.PrintWriter writer) {
        List<BreakpointTemplate> templates = BreakpointLibrary.defaults().all();
        writer.println("  [条件断点] " + templates.size() + " 个模板");
        templates.forEach(t -> writer.println("           · " + t.id() + " — " + t.name()));
    }

    private void writeHotReload(java.io.PrintWriter writer, Path dir) {
        JvmProbe probe = JvmProbe.system();
        JvmKind kind = jvm != null ? jvm : probe.kind();
        HotReloadReport report = new HotReloadCapability()
                .evaluate(instanceId + "-" + change.name(), kind, change);

        if (log != null && Files.isRegularFile(log)) {
            try {
                report = new ReloadMisuseDetector().apply(report, Files.readAllLines(log, StandardCharsets.UTF_8));
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        writer.println("  [热重载] JVM=" + kind + "（" + probe.description() + "）改动=" + change.displayName());
        writer.println("           可热生效=" + report.hotSwappable() + " 需要重启=" + report.requiresRestart());
        writer.println("           建议：" + report.recommendation());
        if (report.marked()) {
            writer.println("           " + report.marker() + " 发布门禁放行=" + report.releaseGateAllowed());
        }

        Path matrix = dir.resolve("hotreload-matrix.md");
        write(matrix, new HotReloadMatrix().toMarkdown());
        writer.println("  [热重载] 能力自检矩阵 " + matrix.toAbsolutePath());
    }

    private void writeColdStart(java.io.PrintWriter writer) {
        ColdStartLifecycle cold = new ColdStartLifecycle();
        List<String> plan = cold.plan();
        var validation = cold.validate(plan);
        writer.println("  [冷启动轨] 步骤 " + String.join(" → ", plan));
        writer.println("           校验通过=" + validation.accepted()
                + (validation.reason() == null ? "" : " 说明=" + validation.reason()));
    }

    private void writeResourceRoute(java.io.PrintWriter writer) {
        if (changedPath == null || changedPath.isBlank()) {
            return;
        }
        var decision = new ResourceChangeRouter().route(changedPath);
        writer.println("  [资源路由] " + decision.path() + " → " + decision.action()
                + "（轨 " + decision.track().displayName() + "：" + decision.reason() + "）");
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
