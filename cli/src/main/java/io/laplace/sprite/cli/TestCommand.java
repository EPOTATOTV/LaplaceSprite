package io.laplace.sprite.cli;

import io.laplace.sprite.adapters.AbstractLaplaceAdapter;
import io.laplace.sprite.adapters.AdapterRegistryFactory;
import io.laplace.sprite.adapters.SmokeScenarios;
import io.laplace.sprite.adapters.ecosystem.MatrixHealthRegistry;
import io.laplace.sprite.adapters.matrix.AffectedFamilySelector;
import io.laplace.sprite.adapters.matrix.MatrixPlan;
import io.laplace.sprite.adapters.matrix.VersionMatrixRegistry;
import io.laplace.sprite.adapters.pipeline.DirectoryFileSink;
import io.laplace.sprite.adapters.pipeline.InstallPipeline;
import io.laplace.sprite.adapters.pipeline.InstallPipelineResult;
import io.laplace.sprite.core.model.Artifact;
import io.laplace.sprite.core.model.ArtifactRole;
import io.laplace.sprite.core.model.ArtifactSpec;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.model.RunTarget;
import io.laplace.sprite.core.model.ServerPluginConfig;
import io.laplace.sprite.core.spi.AdapterRegistry;
import io.laplace.sprite.core.spi.InstallContext;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * {@code laplace test}：适配器矩阵冒烟（14.8 六步中的第 3 步）。
 *
 * <p>三个冒烟剧本：装配成功 / 核心事件归一 / 生命周期全链路。CI 矩阵按
 * {@code laplace test --platform ... --mc ... --adapter ...} 调用本命令。</p>
 *
 * <p>{@code --pipeline} 换用 14.5 的五步装配流水线（resolve / layout / install / launch / verify）；
 * {@code --affected} + {@code --plan} 输出 14.7 的「第一梯队 + 受影响族」CI 矩阵片段。</p>
 */
@Command(name = "test", description = "适配器矩阵冒烟：装配成功 / 核心事件归一 / 生命周期全链路",
        mixinStandardHelpOptions = true)
final class TestCommand implements Callable<Integer> {

    @Option(names = "--platform", defaultValue = "paper", description = "目标平台")
    String platform;

    @Option(names = "--mc", defaultValue = Support.DEFAULT_MC, description = "Minecraft 版本")
    String mc;

    @Option(names = "--adapter", description = "加载器版本（模组线）")
    String loader;

    @Option(names = "--all", description = "跑全部适配器")
    boolean all;

    @Option(names = "--pipeline", description = "改用 14.5 五步装配流水线（含 verify 健康检查）")
    boolean pipeline;

    @Option(names = "--affected", description = "改动的文件路径，可重复；用于 14.7 受影响族选择")
    List<String> affected;

    @Option(names = "--plan", description = "只输出 CI 矩阵计划片段，不跑冒烟")
    boolean planOnly;

    @Option(names = "--workspace", description = "流水线工作目录，默认 build/laplace/install")
    Path workspace;

    @Option(names = "--health", description = "矩阵健康度文件；提供后把本次冒烟结果写回")
    Path health;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        AdapterRegistry registry = AdapterRegistryFactory.defaultRegistry();
        var out = spec.commandLine().getOut();
        MatrixHealthRegistry healthRegistry = health == null ? null : new MatrixHealthRegistry(health);

        AffectedFamilySelector.Selection selection = null;
        if (affected != null && !affected.isEmpty()) {
            selection = new AffectedFamilySelector().select(affected);
            out.printf("受影响族：%s（%s）%n",
                    selection.families().isEmpty() ? "无" : selection.families(),
                    selection.reason());
        }

        if (planOnly) {
            AffectedFamilySelector.Selection effective = selection != null
                    ? selection
                    : new AffectedFamilySelector().select(List.of());
            MatrixPlan plan = MatrixPlan.forPullRequest(effective, VersionMatrixRegistry.defaults(false));
            out.print(plan.toMatrixYaml());
            out.println();
            return 0;
        }

        SmokeScenarios smoke = new SmokeScenarios();
        List<SmokeScenarios.SmokeResult> results = new ArrayList<>();
        List<InstallPipelineResult> pipelines = new ArrayList<>();

        if (all) {
            for (AbstractLaplaceAdapter adapter : AdapterRegistryFactory.allAdapters()) {
                if (selection != null && !selection.families().contains(adapter.family())) {
                    continue;
                }
                PlatformKey adapterKey = keyOf(adapter, mc, loader);
                if (pipeline) {
                    InstallPipelineResult result = runPipeline(adapter, adapterKey);
                    pipelines.add(result);
                    recordHealth(healthRegistry, adapterKey, result.allOk());
                } else {
                    List<SmokeScenarios.SmokeResult> adapterResults = smoke.run(adapter, adapterKey);
                    results.addAll(adapterResults);
                    recordHealth(healthRegistry, adapterKey,
                            adapterResults.stream().allMatch(SmokeScenarios.SmokeResult::passed));
                }
            }
        } else {
            PlatformKey key = Support.requirePlatform(spec, platform, mc, loader);
            var adapter = registry.resolve(key);
            if (adapter.isEmpty()) {
                out.printf("矩阵无可用适配器: %s%n", key.id());
                return 1;
            }
            if (pipeline) {
                InstallPipelineResult result = runPipeline(adapter.get(), key);
                pipelines.add(result);
                recordHealth(healthRegistry, key, result.allOk());
            } else {
                List<SmokeScenarios.SmokeResult> adapterResults = smoke.run(adapter.get(), key);
                results.addAll(adapterResults);
                recordHealth(healthRegistry, key,
                        adapterResults.stream().allMatch(SmokeScenarios.SmokeResult::passed));
            }
        }

        long failed;
        if (pipeline) {
            pipelines.forEach(result -> printPipeline(out, result));
            failed = pipelines.stream().filter(result -> !result.allOk()).count();
            out.printf("装配流水线：%d 个适配器，失败 %d 个%n", pipelines.size(), failed);
            if (failed > 0) {
                out.println("  说明：进程内轨没有监听端口，verify 的「端口就绪」项会判失败；"
                        + "真实环境轨才有可探测端口。");
            }
        } else {
            failed = results.stream().filter(r -> !r.passed()).count();
            results.forEach(r -> out.printf("  [%s] %s / %s — %s%n", r.passed() ? "PASS" : "FAIL",
                    r.adapterId(), r.scenario(), r.detail()));
            out.printf("冒烟结果：%d 项，失败 %d 项%n", results.size(), failed);
        }
        registry.warnings().forEach(w -> out.println("  告警：" + w));
        if (healthRegistry != null) {
            out.printf("矩阵健康度：%s（%d 格）%n", health.toAbsolutePath(), healthRegistry.health().size());
            healthRegistry.health().forEach(cell -> out.printf("  %s — 连续通过 %d / 连续失败 %d / %s%n",
                    cell.cellId(), cell.consecutivePasses(), cell.consecutiveFailures(), cell.mode()));
        }
        return failed == 0 ? 0 : 1;
    }

    private static void recordHealth(MatrixHealthRegistry registry, PlatformKey key, boolean passed) {
        if (registry != null) {
            registry.record(key.id(), passed);
        }
    }

    private InstallPipelineResult runPipeline(io.laplace.sprite.core.spi.LaplaceAdapter adapter,
                                              PlatformKey key) {
        Path root = Support.workspace(workspace).resolve(adapter.id());
        ArtifactRole role = adapter.family().isModLoader() ? ArtifactRole.MOD
                : adapter.family().isPluginPlatform() || adapter.family() == PlatformFamily.GEYSER
                ? ArtifactRole.PLUGIN : ArtifactRole.DATAPACK;
        Artifact artifact = adapter.artifactResolver()
                .resolve(ArtifactSpec.maven("io.laplace.sprite:sprite-probe:1.0.0", role));
        InstallContext ctx = new InstallContext(key, RunTarget.detectionEngine(), root,
                List.of(artifact), ServerPluginConfig.safeDefaults(), Map.of(), true, true);
        // 进程内轨无监听端口：如实返回 false，由 verify 阶段报出，而不是假装就绪。
        InstallPipeline pipeline = new InstallPipeline(new DirectoryFileSink(),
                (host, port) -> false, root);
        return pipeline.run(adapter, ctx);
    }

    private static void printPipeline(java.io.PrintWriter out, InstallPipelineResult result) {
        out.printf("  [%s] %s 五步装配%s%n", result.allOk() ? "PASS" : "FAIL", result.instanceId(),
                result.failedStage() == null ? "" : "（失败阶段 " + result.failedStage() + "）");
        result.steps().forEach(step -> out.printf("      %-8s %s — %s%n", step.stage(),
                step.ok() ? "PASS" : "FAIL", step.detail()));
    }

    private static PlatformKey keyOf(AbstractLaplaceAdapter adapter, String mc, String loader) {
        return new PlatformKey(adapter.family(), mc, Support.blankToNull(loader));
    }
}
