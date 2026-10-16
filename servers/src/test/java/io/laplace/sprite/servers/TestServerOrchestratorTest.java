package io.laplace.sprite.servers;

import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.model.RunStatus;
import io.laplace.sprite.core.model.ScenarioRun;
import io.laplace.sprite.core.model.ServerHealth;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 编排控制台（11.3 / 12.2）。 */
class TestServerOrchestratorTest {

    private static final PlatformKey PAPER = PlatformKey.of(PlatformFamily.PAPER, "1.21.11");

    @Test
    void startAllStopsAllAndExposesHealth() {
        TestServerOrchestrator orchestrator = new TestServerOrchestrator(new InProcessDriver());
        orchestrator.startAll(List.of(
                TestServerSpec.inProcess("alpha", PAPER, "1.0.0"),
                TestServerSpec.inProcess("beta", PAPER, "1.0.0")));

        assertEquals(2, orchestrator.instanceCount());
        List<ServerHealth> health = orchestrator.health();
        assertEquals(2, health.size());
        ServerHealth alpha = health.stream().filter(h -> h.instanceId().equals("alpha")).findFirst()
                .orElseThrow();
        assertEquals(20.0, alpha.tps());
        assertEquals(RunStatus.RUNNING, alpha.status());
        assertEquals("1.0.0", alpha.pluginVersion());
        assertTrue(alpha.healthy());

        assertFalse(orchestrator.logs().isEmpty());
        assertEquals(1, orchestrator.logTail(1).size());
        assertTrue(orchestrator.logTail(1).get(0).startsWith("ts="), "日志尾部应为 logfmt 单行");

        orchestrator.stopAll();
        assertEquals(RunStatus.ABORTED, orchestrator.health().get(0).status());
    }

    @Test
    void orchestratorKeepsRunningWhenDockerIsUnavailable() {
        TestServerOrchestrator orchestrator = new TestServerOrchestrator(
                new DockerComposeDriver("definitely-not-a-docker-binary-xyz"));
        orchestrator.startAll(List.of(TestServerSpec.inProcess("gamma", PAPER, "1.0.0")));

        assertEquals(1, orchestrator.instanceCount());
        ServerHealth health = orchestrator.health().get(0);
        assertEquals(RunStatus.SKIPPED, health.status(), "容器轨不可用时该实例标记为 SKIPPED");
        assertEquals(20.0, health.tps(), "指标仍来自合成代理，面板不至于空着");
    }

    @Test
    void scenariosAndRunsAreRecorded() {
        TestServerOrchestrator orchestrator = new TestServerOrchestrator(new InProcessDriver());
        orchestrator.startAll(List.of(TestServerSpec.inProcess("delta", PAPER, "1.0.0")));
        orchestrator.attachScenarios(List.of(new ScenarioRun("pvp", "pvp", 42.0, 3, RunStatus.RUNNING,
                PAPER)));
        assertEquals(1, orchestrator.scenarioRuns().size());
        assertEquals(42.0, orchestrator.scenarioRuns().get(0).progressPercent());
        assertTrue(orchestrator.metrics("delta").isPresent());
    }
}
