package io.laplace.sprite.servers;

import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.model.RunStatus;
import io.laplace.sprite.core.model.ScenarioRun;
import io.laplace.sprite.core.model.ServerHealth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 混合端实例（12.1 / v1.1.0 §14.3）：Arclight / Youer 要同时挂 plugins/ 与 mods/。 */
class HybridInstanceTest {

    private static final PlatformKey ARCLIGHT = PlatformKey.of(PlatformFamily.ARCLIGHT, "1.21.1");

    @Test
    void hybridSpecExposesBothReadOnlyMounts(@TempDir Path tempDir) {
        Path plugins = tempDir.resolve("plugins");
        Path mods = tempDir.resolve("mods");

        TestServerSpec spec = TestServerSpec.hybrid("hy-1", ARCLIGHT, "1.0.0", plugins, mods);
        List<ServerMount> mounts = spec.mounts();

        assertEquals(2, mounts.size());
        assertEquals(plugins.toString(), mounts.get(0).hostPath());
        assertEquals(TestServerSpec.PLUGINS_CONTAINER_PATH, mounts.get(0).containerPath());
        assertEquals(mods.toString(), mounts.get(1).hostPath());
        assertEquals(TestServerSpec.MODS_CONTAINER_PATH, mounts.get(1).containerPath());
        assertTrue(mounts.stream().allMatch(ServerMount::readOnly));
        assertTrue(mounts.get(0).volumeArgument().endsWith(":ro"));
        assertEquals("-Xmx2048M", spec.jvmArgsWithMemory().get(0), "内存参数行为不受目录绑定影响");
    }

    @Test
    void hybridFactoryRejectsNonHybridFamily(@TempDir Path tempDir) {
        PlatformKey paper = PlatformKey.of(PlatformFamily.PAPER, "1.21.1");

        assertThrows(IllegalArgumentException.class,
                () -> TestServerSpec.hybrid("paper-1", paper, "1.0.0", tempDir, tempDir));
    }

    @Test
    void composeRendersBothHybridMounts(@TempDir Path tempDir) {
        TestServerSpec spec = TestServerSpec.hybrid("hy-1", ARCLIGHT, "1.0.0", tempDir.resolve("plugins"),
                tempDir.resolve("mods"));

        String yaml = new DockerComposeDriver("definitely-not-a-docker-binary-xyz", tempDir)
                .composeContent(List.of(spec));

        assertTrue(yaml.contains("/plugins:ro"), yaml);
        assertTrue(yaml.contains("/mods:ro"), yaml);
    }

    @Test
    void orchestratorReportsHealthAndScenariosForHybridInstance(@TempDir Path tempDir) {
        TestServerSpec spec = TestServerSpec.hybrid("hy-1", ARCLIGHT, "1.0.0", tempDir.resolve("plugins"),
                tempDir.resolve("mods"));
        TestServerOrchestrator orchestrator = new TestServerOrchestrator(new InProcessDriver());

        orchestrator.startAll(List.of(spec));
        orchestrator.attachScenarios(List.of(new ScenarioRun("pvp", "pvp", 60.0, 2, RunStatus.RUNNING,
                ARCLIGHT)));

        ServerHealth health = orchestrator.health().get(0);
        assertEquals("hy-1", health.instanceId());
        assertEquals(PlatformFamily.ARCLIGHT, health.platform().family());
        assertEquals(RunStatus.RUNNING, health.status());
        assertEquals(2, orchestrator.mounts("hy-1").size());
        assertTrue(orchestrator.mounts("unknown").isEmpty());
        assertEquals(1, orchestrator.scenarioRuns().size());
        assertTrue(orchestrator.logs().stream().anyMatch(line -> line.message().contains("/plugins")),
                "启动日志应记录目录绑定");
    }
}
