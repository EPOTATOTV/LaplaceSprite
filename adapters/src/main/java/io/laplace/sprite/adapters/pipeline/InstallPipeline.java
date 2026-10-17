package io.laplace.sprite.adapters.pipeline;

import io.laplace.sprite.adapters.lifecycle.SimulatedLifecycleHook;
import io.laplace.sprite.core.model.Artifact;
import io.laplace.sprite.core.model.ArtifactRole;
import io.laplace.sprite.core.model.ArtifactSpec;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.spi.AdapterRegistry;
import io.laplace.sprite.core.spi.ArtifactResolver;
import io.laplace.sprite.core.spi.InstallContext;
import io.laplace.sprite.core.spi.InstallLayout;
import io.laplace.sprite.core.spi.LaplaceAdapter;
import io.laplace.sprite.core.spi.LifecycleHook;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 装配流水线（14.5）：resolve → layout → install → launch → verify，顺序硬约束。
 *
 * <p>任一阶段不通过就返回，后面的阶段不再执行，结果里带上是哪个阶段、为什么失败。
 * 单次运行的状态放在 {@link RunState} 里，流水线本身可以复用。</p>
 */
public final class InstallPipeline {

    /**
     * 真实环境轨的服务端 stdout 放进 attributes 的键。
     *
     * <p>进程与容器编排属于 servers 模块，适配器不管理进程，只消费已捕获的日志做健康检查。</p>
     */
    public static final String LAUNCH_LOG_ATTRIBUTE = "launch.log";

    /** 探针产物坐标：装配阶段需要一个真实可解析的根产物，口径与冒烟剧本一致。 */
    private static final String PROBE_COORDINATE = "io.laplace.sprite:sprite-probe:1.0.0";

    private static final String HOST_ATTRIBUTE = "host";
    private static final String PORT_ATTRIBUTE = "port";
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 25565;

    private final InstallFileSink sink;
    private final PortProbe portProbe;
    private final Path workingRoot;
    private final AdapterRegistry registry;

    /** 不注入注册表：verify 的降级告警检查会显式跳过。 */
    public InstallPipeline(InstallFileSink sink, PortProbe portProbe, Path workingRoot) {
        this(sink, portProbe, workingRoot, null);
    }

    public InstallPipeline(InstallFileSink sink, PortProbe portProbe, Path workingRoot,
                           AdapterRegistry registry) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.portProbe = Objects.requireNonNull(portProbe, "portProbe");
        this.workingRoot = Objects.requireNonNull(workingRoot, "workingRoot");
        this.registry = registry;
    }

    /** 顺序执行五个阶段，失败即中止。 */
    public InstallPipelineResult run(LaplaceAdapter adapter, InstallContext ctx) {
        String instanceId = adapter.id() + "@" + ctx.platform().id();
        List<InstallStepResult> steps = new ArrayList<>();
        RunState state = new RunState();

        steps.add(resolve(adapter, ctx, state));
        if (!lastOk(steps)) {
            return result(instanceId, steps);
        }
        steps.add(layout(adapter, ctx, state));
        if (!lastOk(steps)) {
            return result(instanceId, steps);
        }
        steps.add(install(ctx, state));
        if (!lastOk(steps)) {
            return result(instanceId, steps);
        }
        steps.add(launch(adapter, ctx, state));
        if (!lastOk(steps)) {
            return result(instanceId, steps);
        }
        steps.add(verify(ctx, state));
        return result(instanceId, steps);
    }

    /** resolve：解析根产物坐标，补齐依赖闭包（含上下文里已解析的产物）。 */
    private InstallStepResult resolve(LaplaceAdapter adapter, InstallContext ctx, RunState state) {
        long start = System.nanoTime();
        try {
            ArtifactResolver resolver = adapter.artifactResolver();
            List<Artifact> closure = new ArrayList<>();
            Artifact root = resolver.resolve(
                    ArtifactSpec.maven(PROBE_COORDINATE, roleFor(adapter.family())));
            addUnique(closure, root);
            resolver.resolveDependencies(root).forEach(artifact -> addUnique(closure, artifact));
            for (Artifact declared : ctx.artifacts()) {
                addUnique(closure, declared);
                resolver.resolveDependencies(declared).forEach(artifact -> addUnique(closure, artifact));
            }
            state.resolved = List.copyOf(closure);
            return InstallStepResult.ok(InstallStage.RESOLVE,
                    "解析产物 %d 项: %s".formatted(state.resolved.size(), ids(state.resolved)),
                    elapsed(start));
        } catch (RuntimeException ex) {
            return InstallStepResult.failed(InstallStage.RESOLVE, "解析失败: " + ex.getMessage(),
                    List.of("解析失败: " + ex.getMessage()), elapsed(start));
        }
    }

    /** layout：目标目录 + 依赖三语义加载顺序。 */
    private InstallStepResult layout(LaplaceAdapter adapter, InstallContext ctx, RunState state) {
        long start = System.nanoTime();
        try {
            InstallLayout layout = adapter.installLayout();
            state.targetDirectory = workingRoot.resolve(layout.layoutDirectory(ctx.platform()));
            state.ordered = layout.orderByDependencies(state.resolved);
            return InstallStepResult.ok(InstallStage.LAYOUT,
                    "目标目录 %s；加载顺序 %s".formatted(state.targetDirectory, ids(state.ordered)),
                    elapsed(start));
        } catch (RuntimeException ex) {
            return InstallStepResult.failed(InstallStage.LAYOUT, "布局失败: " + ex.getMessage(),
                    List.of("布局失败: " + ex.getMessage()), elapsed(start));
        }
    }

    /** install：写入目标目录，校验哈希 / 签名与 EULA 声明。 */
    private InstallStepResult install(InstallContext ctx, RunState state) {
        long start = System.nanoTime();
        List<String> warnings = new ArrayList<>();
        try {
            for (Artifact artifact : state.ordered) {
                InstallFileSink.WrittenFile written = sink.write(state.targetDirectory, artifact);
                verifyHash(artifact, written).ifPresent(warnings::add);
                verifySignature(artifact, written).ifPresent(warnings::add);
            }
            if (!ctx.eulaAccepted() && !sink.hasEulaDeclaration(workingRoot)) {
                warnings.add("EULA 未声明：工作根 %s 下没有 eula.txt，环境变量 EULA 也不是 TRUE，装配中止"
                        .formatted(workingRoot));
            }
            String detail = "写入 %s（%d 项）".formatted(state.targetDirectory, state.ordered.size());
            return warnings.isEmpty()
                    ? InstallStepResult.ok(InstallStage.INSTALL, detail, elapsed(start))
                    : InstallStepResult.failed(InstallStage.INSTALL, detail, warnings, elapsed(start));
        } catch (RuntimeException ex) {
            return InstallStepResult.failed(InstallStage.INSTALL, "写入失败: " + ex.getMessage(),
                    List.of("写入失败: " + ex.getMessage()), elapsed(start));
        }
    }

    /** launch：冷启动生命周期接线。 */
    private InstallStepResult launch(LaplaceAdapter adapter, InstallContext ctx, RunState state) {
        long start = System.nanoTime();
        try {
            LifecycleHook hook = adapter.lifecycleHook();
            // 冷启动全生命周期：onLoad → onEnable → 事件订阅。不走 /reload 热加载语义
            // （Paper 1.21.7 已弃用 /reload），装配验证一律按进程重启口径设计。
            hook.onLoad(ctx);
            hook.onEnable(ctx);
            if (hook instanceof SimulatedLifecycleHook simulated) {
                state.log.addAll(simulated.history());
            }
            state.log.addAll(launchLog(ctx));
            state.launched = true;
            // 真实轨道的容器 / 独立服务器编排在 servers 模块，适配器只负责生命周期与探针接线。
            return InstallStepResult.ok(InstallStage.LAUNCH,
                    "冷启动完成：onLoad → onEnable → 事件订阅；日志 %d 行".formatted(state.log.size()),
                    elapsed(start));
        } catch (RuntimeException ex) {
            return InstallStepResult.failed(InstallStage.LAUNCH, "拉起失败: " + ex.getMessage(),
                    List.of("拉起失败: " + ex.getMessage()), elapsed(start));
        }
    }

    /** verify：四类健康检查各出一条结论，任一不过即阶段失败。 */
    private InstallStepResult verify(InstallContext ctx, RunState state) {
        long start = System.nanoTime();
        List<String> checks = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        boolean loaded = state.launched;
        checks.add(check("加载成功", loaded, "onLoad / onEnable 无异常完成", "生命周期未完成"));
        if (!loaded) {
            failures.add("加载成功：生命周期未完成");
        }

        String host = host(ctx);
        int port = port(ctx);
        boolean portReady = portProbe.isOpen(host, port);
        checks.add(check("端口就绪", portReady, "%s:%d 已监听".formatted(host, port),
                "%s:%d 未监听".formatted(host, port)));
        if (!portReady) {
            failures.add("端口就绪：%s:%d 未监听".formatted(host, port));
        }

        List<String> errorLines = state.log.stream().filter(InstallPipeline::looksLikeError).toList();
        boolean noErrors = errorLines.isEmpty();
        checks.add(check("日志无 ERROR", noErrors, "装配 / 生命周期日志无 ERROR 行",
                "日志出现 %d 行 ERROR 或栈帧：%s".formatted(errorLines.size(),
                        noErrors ? "" : errorLines.get(0))));
        if (!noErrors) {
            failures.add("日志无 ERROR：出现 %d 行 ERROR 或栈帧".formatted(errorLines.size()));
        }

        if (registry == null) {
            checks.add("无版本降级告警 = 未注入注册表，跳过");
        } else {
            List<String> downgrades = downgradeWarnings(ctx);
            checks.add(check("无版本降级告警", downgrades.isEmpty(), "矩阵精确命中，无兼容链回退",
                    "兼容链回退告警：%s".formatted(String.join("；", downgrades))));
            if (!downgrades.isEmpty()) {
                failures.add("无版本降级告警：%s".formatted(String.join("；", downgrades)));
            }
        }

        return failures.isEmpty()
                ? InstallStepResult.ok(InstallStage.VERIFY, String.join("；", checks), elapsed(start))
                : InstallStepResult.failed(InstallStage.VERIFY, String.join("；", checks), failures,
                        elapsed(start));
    }

    /** 哈希只在产物声明了 sha512 时校验；声明了就必须一致。 */
    private static Optional<String> verifyHash(Artifact artifact, InstallFileSink.WrittenFile written) {
        String declared = artifact.sha512();
        if (declared == null || declared.isBlank() || declared.equalsIgnoreCase(written.sha512())) {
            return Optional.empty();
        }
        return Optional.of("哈希校验失败：%s 声明 %s，实际 %s"
                .formatted(artifact.id(), declared, written.sha512()));
    }

    /** 签名只做声明与落盘的一致性对齐，真实验签需要外部公钥环。 */
    private static Optional<String> verifySignature(Artifact artifact, InstallFileSink.WrittenFile written) {
        String declared = artifact.metadata().get(DirectoryFileSink.SIGNATURE_KEY);
        if (declared == null || declared.isBlank() || declared.equals(written.signature())) {
            return Optional.empty();
        }
        return Optional.of("签名校验失败：%s 声明 %s，实际 %s"
                .formatted(artifact.id(), declared, written.signature()));
    }

    /** 解析矩阵时新增的兼容链回退告警。 */
    private List<String> downgradeWarnings(InstallContext ctx) {
        int before = registry.warnings().size();
        registry.resolve(ctx.platform());
        List<String> all = registry.warnings();
        return all.size() > before ? List.copyOf(all.subList(before, all.size())) : List.of();
    }

    private static List<String> launchLog(InstallContext ctx) {
        Object value = ctx.attributes().get(LAUNCH_LOG_ATTRIBUTE);
        if (value instanceof List<?> lines) {
            return lines.stream().map(String::valueOf).toList();
        }
        if (value instanceof String textIfPresent && !textIfPresent.isBlank()) {
            return textIfPresent.lines().toList();
        }
        return List.of();
    }

    private static String host(InstallContext ctx) {
        Object value = ctx.attributes().get(HOST_ATTRIBUTE);
        return value == null || value.toString().isBlank() ? DEFAULT_HOST : value.toString().trim();
    }

    private static int port(InstallContext ctx) {
        Object value = ctx.attributes().get(PORT_ATTRIBUTE);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return DEFAULT_PORT;
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException ex) {
            return DEFAULT_PORT;
        }
    }

    private static boolean looksLikeError(String line) {
        return line.contains("ERROR") || line.contains("Exception") || line.contains("\tat ");
    }

    private static String check(String name, boolean passed, String okDetail, String failDetail) {
        return "%s=%s（%s）".formatted(name, passed ? "通过" : "失败", passed ? okDetail : failDetail);
    }

    private static void addUnique(List<Artifact> artifacts, Artifact artifact) {
        boolean present = artifacts.stream().anyMatch(existing -> existing.id().equals(artifact.id()));
        if (!present) {
            artifacts.add(artifact);
        }
    }

    private static List<String> ids(List<Artifact> artifacts) {
        return artifacts.stream().map(Artifact::id).toList();
    }

    /** 探针产物角色：与装配目录口径一致——模组走 mods/、插件平台走 plugins/、其余按数据包。 */
    private static ArtifactRole roleFor(PlatformFamily family) {
        if (family.isModLoader()) {
            return ArtifactRole.MOD;
        }
        if (family.isPluginPlatform() || family == PlatformFamily.GEYSER) {
            return ArtifactRole.PLUGIN;
        }
        return ArtifactRole.DATAPACK;
    }

    private static boolean lastOk(List<InstallStepResult> steps) {
        return steps.get(steps.size() - 1).ok();
    }

    private static long elapsed(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private static InstallPipelineResult result(String instanceId, List<InstallStepResult> steps) {
        return new InstallPipelineResult(instanceId, steps,
                steps.stream().allMatch(InstallStepResult::ok));
    }

    /** 单次运行的可变状态：解析结果、目标目录、加载顺序与日志。 */
    private static final class RunState {

        private List<Artifact> resolved = List.of();
        private List<Artifact> ordered = List.of();
        private Path targetDirectory;
        private final List<String> log = new ArrayList<>();
        private boolean launched;
    }
}
