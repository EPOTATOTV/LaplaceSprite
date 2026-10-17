package io.laplace.sprite.adapters;

import io.laplace.sprite.adapters.bukkit.PaperAdapter;
import io.laplace.sprite.adapters.event.EventBridges;
import io.laplace.sprite.adapters.lifecycle.SimulatedLifecycleHook;
import io.laplace.sprite.adapters.pipeline.InstallFileSink;
import io.laplace.sprite.adapters.pipeline.InstallPipeline;
import io.laplace.sprite.adapters.pipeline.InstallPipelineResult;
import io.laplace.sprite.adapters.pipeline.InstallStage;
import io.laplace.sprite.core.model.Artifact;
import io.laplace.sprite.core.model.ArtifactDependency;
import io.laplace.sprite.core.model.ArtifactRole;
import io.laplace.sprite.core.model.ArtifactSource;
import io.laplace.sprite.core.model.ArtifactSpec;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.model.RunTarget;
import io.laplace.sprite.core.model.ServerPluginConfig;
import io.laplace.sprite.core.spi.AdapterRegistry;
import io.laplace.sprite.core.spi.ArtifactResolver;
import io.laplace.sprite.core.spi.EventBridge;
import io.laplace.sprite.core.spi.InstallContext;
import io.laplace.sprite.core.spi.InstallLayout;
import io.laplace.sprite.core.spi.LaplaceAdapter;
import io.laplace.sprite.core.spi.LifecycleHook;
import io.laplace.sprite.core.spi.SupportRange;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 装配流水线五步（14.5）：顺序硬约束、校验口径与健康检查。 */
class InstallPipelineTest {

    private static final PlatformKey PAPER_KEY = PlatformKey.of(PlatformFamily.PAPER, "1.21.11");
    private static final Path ROOT = Path.of("build", "test-install-root");

    @Test
    void runsAllFiveStagesInOrder() {
        FakeSink sink = new FakeSink();

        InstallPipelineResult result = pipeline(sink, true, null).run(new PaperAdapter(),
                context(PAPER_KEY, List.of(), Map.of(), true));

        assertTrue(result.allOk(), result.toMarkdown());
        assertEquals(List.of(InstallStage.RESOLVE, InstallStage.LAYOUT, InstallStage.INSTALL,
                InstallStage.LAUNCH, InstallStage.VERIFY),
                result.steps().stream().map(step -> step.stage()).toList());
        assertEquals(1, sink.writes, "根产物必须落盘一次");
        assertTrue(result.failedStage().isEmpty());
        assertTrue(result.toMarkdown().contains("| RESOLVE | 通过 |"), result.toMarkdown());
    }

    @Test
    void missingEulaFailsInstallAndStopsThePipeline() {
        FakeSink sink = new FakeSink();

        InstallPipelineResult result = pipeline(sink, true, null).run(new PaperAdapter(),
                context(PAPER_KEY, List.of(), Map.of(), false));

        assertEquals(Optional.of(InstallStage.INSTALL), result.failedStage());
        assertEquals(3, result.steps().size(), "INSTALL 失败后不得再跑 LAUNCH / VERIFY");
        assertTrue(result.stage(InstallStage.INSTALL).orElseThrow().warnings().stream()
                .anyMatch(warning -> warning.contains("EULA 未声明")), result.toMarkdown());
    }

    @Test
    void eulaDeclarationFromSinkSatisfiesTheCheck() {
        FakeSink sink = new FakeSink();
        sink.eulaDeclared = true;

        InstallPipelineResult result = pipeline(sink, true, null).run(new PaperAdapter(),
                context(PAPER_KEY, List.of(), Map.of(), false));

        assertTrue(result.allOk(), result.toMarkdown());
    }

    @Test
    void missingHardDependencyFailsLayoutWithoutWritingAnything() {
        FakeSink sink = new FakeSink();
        Artifact root = artifact("probe", null, Map.of());
        Artifact child = new Artifact("child", "child", "1.0.0", ArtifactSource.MAVEN,
                "io.laplace.test:child:1.0.0", null, ArtifactRole.PLUGIN,
                List.of(ArtifactDependency.hard("ghost")), Map.of());

        InstallPipelineResult result = pipeline(sink, true, null)
                .run(new StubAdapter(PlatformFamily.PAPER, new StubResolver(root, List.of(child))),
                        context(PAPER_KEY, List.of(), Map.of(), true));

        assertEquals(Optional.of(InstallStage.LAYOUT), result.failedStage());
        assertEquals(2, result.steps().size(), "LAYOUT 失败后不得再跑 INSTALL");
        assertEquals(0, sink.writes);
        assertTrue(result.stage(InstallStage.LAYOUT).orElseThrow().warnings().stream()
                .anyMatch(warning -> warning.contains("硬依赖")), result.toMarkdown());
    }

    @Test
    void hashMismatchFailsInstall() {
        FakeSink sink = new FakeSink();
        sink.written = new InstallFileSink.WrittenFile("probe.jar", 8, "0000", null);
        Artifact root = artifact("probe", "deadbeef", Map.of());

        InstallPipelineResult result = pipeline(sink, true, null)
                .run(new StubAdapter(PlatformFamily.PAPER, new StubResolver(root, List.of())),
                        context(PAPER_KEY, List.of(), Map.of(), true));

        assertEquals(Optional.of(InstallStage.INSTALL), result.failedStage());
        assertTrue(result.stage(InstallStage.INSTALL).orElseThrow().warnings().stream()
                .anyMatch(warning -> warning.contains("哈希校验失败")), result.toMarkdown());
    }

    @Test
    void signatureMismatchFailsInstall() {
        FakeSink sink = new FakeSink();
        sink.written = new InstallFileSink.WrittenFile("probe.jar", 8, null, "SHA256-BBB");
        Artifact root = artifact("probe", null, Map.of("signature", "SHA256-AAA"));

        InstallPipelineResult result = pipeline(sink, true, null)
                .run(new StubAdapter(PlatformFamily.PAPER, new StubResolver(root, List.of())),
                        context(PAPER_KEY, List.of(), Map.of(), true));

        assertEquals(Optional.of(InstallStage.INSTALL), result.failedStage());
        assertTrue(result.stage(InstallStage.INSTALL).orElseThrow().warnings().stream()
                .anyMatch(warning -> warning.contains("签名校验失败")), result.toMarkdown());
    }

    @Test
    void hashAndSignaturePassWhenTheSinkAgrees() {
        FakeSink sink = new FakeSink();
        sink.written = new InstallFileSink.WrittenFile("probe.jar", 8, "deadbeef", "SHA256-AAA");
        Artifact root = artifact("probe", "deadbeef", Map.of("signature", "SHA256-AAA"));

        InstallPipelineResult result = pipeline(sink, true, null)
                .run(new StubAdapter(PlatformFamily.PAPER, new StubResolver(root, List.of())),
                        context(PAPER_KEY, List.of(), Map.of(), true));

        assertTrue(result.allOk(), result.toMarkdown());
    }

    @Test
    void closedPortFailsVerify() {
        InstallPipelineResult result = pipeline(new FakeSink(), false, null).run(new PaperAdapter(),
                context(PAPER_KEY, List.of(), Map.of(), true));

        assertEquals(Optional.of(InstallStage.VERIFY), result.failedStage());
        assertTrue(result.stage(InstallStage.VERIFY).orElseThrow().warnings().stream()
                .anyMatch(warning -> warning.contains("端口就绪")), result.toMarkdown());
    }

    @Test
    void errorLinesInLaunchLogFailVerify() {
        Map<String, Object> attributes = Map.of(InstallPipeline.LAUNCH_LOG_ATTRIBUTE, List.of(
                "[12:00:00] [Server thread/INFO]: Done (3.104s)! For help, type \"help\"",
                "[12:00:01] [Server thread/ERROR]: java.lang.IllegalStateException: spawn failed",
                "\tat io.laplace.sprite.probe.Boot.onEnable(Boot.java:42)"));

        InstallPipelineResult result = pipeline(new FakeSink(), true, null).run(new PaperAdapter(),
                context(PAPER_KEY, List.of(), attributes, true));

        assertEquals(Optional.of(InstallStage.VERIFY), result.failedStage());
        assertTrue(result.stage(InstallStage.VERIFY).orElseThrow().warnings().stream()
                .anyMatch(warning -> warning.contains("日志无 ERROR")), result.toMarkdown());
    }

    @Test
    void compatChainDowngradeWarningFailsVerify() {
        AdapterRegistry registry = new AdapterRegistry();
        registry.register(new PaperAdapter());
        PlatformKey purpur = PlatformKey.of(PlatformFamily.PURPUR, "1.21.11");

        InstallPipelineResult result = pipeline(new FakeSink(), true, registry)
                .run(new PaperAdapter(), context(purpur, List.of(), Map.of(), true));

        assertEquals(Optional.of(InstallStage.VERIFY), result.failedStage());
        assertTrue(result.stage(InstallStage.VERIFY).orElseThrow().warnings().stream()
                .anyMatch(warning -> warning.contains("无版本降级告警")), result.toMarkdown());
    }

    @Test
    void exactMatrixHitKeepsVerifyGreen() {
        AdapterRegistry registry = new AdapterRegistry();
        registry.register(new PaperAdapter());

        InstallPipelineResult result = pipeline(new FakeSink(), true, registry).run(new PaperAdapter(),
                context(PAPER_KEY, List.of(), Map.of(), true));

        assertTrue(result.allOk(), result.toMarkdown());
    }

    private static InstallPipeline pipeline(InstallFileSink sink, boolean portOpen, AdapterRegistry registry) {
        return new InstallPipeline(sink, (host, port) -> portOpen, ROOT, registry);
    }

    private static InstallContext context(PlatformKey key, List<Artifact> artifacts,
                                          Map<String, Object> attributes, boolean eulaAccepted) {
        return new InstallContext(key, RunTarget.detectionEngine(), ROOT, artifacts,
                ServerPluginConfig.safeDefaults(), attributes, eulaAccepted, true);
    }

    private static Artifact artifact(String id, String sha512, Map<String, String> metadata) {
        return new Artifact(id, id, "1.0.0", ArtifactSource.MAVEN, "io.laplace.test:" + id + ":1.0.0",
                sha512, ArtifactRole.PLUGIN, List.of(), metadata);
    }

    /** 只走内存的写入出口：测试绝不碰真实服务端目录。 */
    private static final class FakeSink implements InstallFileSink {

        private InstallFileSink.WrittenFile written =
                new InstallFileSink.WrittenFile("probe-1.0.0.jar", 8, null, null);
        private boolean eulaDeclared;
        private int writes;

        @Override
        public WrittenFile write(Path targetDirectory, Artifact artifact) {
            writes++;
            return written;
        }

        @Override
        public boolean hasEulaDeclaration(Path workingRoot) {
            return eulaDeclared;
        }
    }

    /** 固定解析结果的适配器，用来构造哈希 / 签名 / 依赖缺失等边界。 */
    private static final class StubAdapter implements LaplaceAdapter {

        private final PlatformFamily family;
        private final ArtifactResolver resolver;

        private StubAdapter(PlatformFamily family, ArtifactResolver resolver) {
            this.family = family;
            this.resolver = resolver;
        }

        @Override
        public String id() {
            return "stub-" + family.name().toLowerCase();
        }

        @Override
        public PlatformFamily family() {
            return family;
        }

        @Override
        public SupportRange supportRange() {
            return SupportRange.any();
        }

        @Override
        public ArtifactResolver artifactResolver() {
            return resolver;
        }

        @Override
        public InstallLayout installLayout() {
            return InstallLayouts.forFamily(family);
        }

        @Override
        public LifecycleHook lifecycleHook() {
            return new SimulatedLifecycleHook();
        }

        @Override
        public EventBridge eventBridge() {
            return EventBridges.of(family);
        }
    }

    private static final class StubResolver implements ArtifactResolver {

        private final Artifact root;
        private final List<Artifact> dependencies;

        private StubResolver(Artifact root, List<Artifact> dependencies) {
            this.root = root;
            this.dependencies = dependencies;
        }

        @Override
        public Artifact resolve(ArtifactSpec spec) {
            return root;
        }

        @Override
        public List<Artifact> resolveDependencies(Artifact artifact) {
            return artifact.id().equals(root.id()) ? dependencies : List.of();
        }
    }
}
