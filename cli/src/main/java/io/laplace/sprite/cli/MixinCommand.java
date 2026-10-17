package io.laplace.sprite.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.mixin.InjectionDeclaration;
import io.laplace.sprite.mixin.InjectionGate;
import io.laplace.sprite.mixin.InjectionGateResult;
import io.laplace.sprite.mixin.MixinJvmFlags;
import io.laplace.sprite.mixin.MixinTargetGraph;
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
 * {@code laplace mixin}：15.11 Mixin 生态适配轨。
 *
 * <p>打印 CI 启动参数；给了启动日志就从日志里扫描注入失败（硬门禁），
 * 给了注入声明就输出 target→mod 冲突图。注入点数量核对需要真实实例的观测值，
 * 命令行不伪造这个数字——它由 {@code MixinJvmFlags} 的计数开关在启动期完成。</p>
 */
@Command(name = "mixin",
        description = "Mixin 轨：注入计数启动参数、启动日志失败扫描、冲突归因图",
        mixinStandardHelpOptions = true)
final class MixinCommand implements Callable<Integer> {

    @Option(names = "--agent", description = "sponge-mixin jar 路径；给定时追加 -javaagent")
    String agentJar;

    @Option(names = "--declarations", description = "注入声明 JSON 数组（字段同 InjectionDeclaration）")
    Path declarations;

    @Option(names = "--log", description = "实例启动日志，用于扫描注入失败")
    Path log;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        var writer = spec.commandLine().getOut();
        MixinJvmFlags flags = agentJar == null || agentJar.isBlank()
                ? MixinJvmFlags.ciDefault()
                : MixinJvmFlags.withAgent(agentJar);

        writer.println("Mixin 轨（15.11）");
        writer.println("  [启动参数] " + flags.render());
        writer.println("            countInjections 开启后，require/expect 声明的注入点数量在启动期核对，"
                + "不符即抛 InvalidInjectionException 阻止启动");

        List<InjectionDeclaration> declared = readDeclarations();
        if (!declared.isEmpty()) {
            writer.printf("  [注入声明] %d 条%n", declared.size());
            for (InjectionDeclaration declaration : declared) {
                writer.printf("    · %s → %s %s（声明 %d 个 · %s）%n", declaration.modId(),
                        declaration.targetClass(), declaration.kind().annotation(),
                        declaration.declaredCount(), declaration.requireMode());
            }
            MixinTargetGraph graph = MixinTargetGraph.from(declared);
            var conflicts = graph.conflicts();
            writer.printf("  [冲突图] 候选 %d 个%n", conflicts.size());
            conflicts.forEach(conflict -> writer.printf("    · %s ← %s（风险 %s）%n",
                    conflict.targetClass(), conflict.participants(), conflict.risk()));
            writer.println("           注入点数量核对需要真实实例的观测计数，命令行不编造该数字");
        }

        if (log != null) {
            if (!Files.isRegularFile(log)) {
                throw new CommandLine.ParameterException(spec.commandLine(),
                        "启动日志不存在: " + log.toAbsolutePath());
            }
            InjectionGateResult result = InjectionGate.fromLaunchLog(read(log));
            writer.printf("  [启动日志] 通过=%s · 失败 %d%n", result.passed(),
                    result.failures().size());
            result.failures().forEach(failure -> writer.println("    - " + failure));
            return result.passed() ? 0 : 1;
        }
        return 0;
    }

    private List<InjectionDeclaration> readDeclarations() {
        if (declarations == null) {
            return List.of();
        }
        if (!Files.isRegularFile(declarations)) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "注入声明文件不存在: " + declarations.toAbsolutePath());
        }
        try {
            return Json.jsonMapper().readValue(read(declarations),
                    new TypeReference<List<InjectionDeclaration>>() {
                    });
        } catch (IOException ex) {
            throw new UncheckedIOException("解析注入声明失败: " + declarations, ex);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
