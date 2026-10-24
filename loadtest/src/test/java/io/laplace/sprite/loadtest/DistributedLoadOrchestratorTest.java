package io.laplace.sprite.loadtest;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 分布式调度（L-07）：命令下发与节点判定聚合。 */
class DistributedLoadOrchestratorTest {

    private final LoadScenario scenario = new LoadScenario("distributed", BotBackend.MINEFLAYER,
            List.of(BotAction.JOIN, BotAction.CHUNK_LOAD), 600,
            new RampCurve(10, 10, 100, 100), "");

    private final DistributedLoadPlan plan = DistributedLoadPlan.split(scenario, List.of(
            new DistributedLoadPlan.Node("node-a", DistributedLoadPlan.NodeTransport.SSH, "10.0.0.1", 1),
            new DistributedLoadPlan.Node("node-b", DistributedLoadPlan.NodeTransport.DOCKER, "10.0.0.2", 1)));

    @Test
    void sendsOneCommandPerNodeAndAggregatesPass() {
        List<String> commands = new ArrayList<>();
        DistributedLoadOrchestrator orchestrator = new DistributedLoadOrchestrator((node, command) -> {
            commands.add(command);
            return DistributedLoadOrchestrator.Execution.fromExitCode(0, "ok");
        });

        DistributedLoadOrchestrator.Dispatch dispatch = orchestrator.dispatch(plan, scenario);

        assertTrue(dispatch.passed());
        assertEquals(2, dispatch.outcomes().size());
        assertEquals(2, commands.size());
        assertTrue(commands.get(0).startsWith("ssh 10.0.0.1 laplace loadtest"));
        assertTrue(commands.get(0).contains("--bots 50"));
        assertTrue(commands.get(1).startsWith("docker run --rm laplace-cli laplace loadtest"));
        assertTrue(dispatch.aggregate().violations().isEmpty());
    }

    @Test
    void anyNodeFailureFailsTheWholeDispatch() {
        DistributedLoadOrchestrator orchestrator = new DistributedLoadOrchestrator((node, command) ->
                node.id().equals("node-b")
                        ? DistributedLoadOrchestrator.Execution.fromExitCode(2, "tps below gate")
                        : DistributedLoadOrchestrator.Execution.fromExitCode(0, "ok"));

        DistributedLoadOrchestrator.Dispatch dispatch = orchestrator.dispatch(plan, scenario);

        assertFalse(dispatch.passed());
        assertEquals(2, dispatch.outcomes().size());
        assertTrue(dispatch.aggregate().violations().get(0).contains("node-b"));
        assertTrue(dispatch.aggregate().violations().get(0).contains("tps below gate"));
    }

    @Test
    void unhealthyNodeIsSkippedAndItsBotsAreRedistributed() {
        List<String> commands = new ArrayList<>();
        DistributedLoadOrchestrator orchestrator = new DistributedLoadOrchestrator(
                (node, command) -> {
                    commands.add(command);
                    return DistributedLoadOrchestrator.Execution.fromExitCode(0, "ok");
                },
                node -> !node.id().equals("node-b"),
                2);

        DistributedLoadOrchestrator.Dispatch dispatch = orchestrator.dispatch(plan, scenario);

        assertTrue(dispatch.passed());
        assertTrue(dispatch.degraded());
        assertEquals(List.of("node-b"), dispatch.skippedNodes());
        assertEquals(1, commands.size(), "只向存活节点下发");
        assertTrue(commands.get(0).contains("--bots 100"), "存活节点接管全部并发");
        assertTrue(dispatch.outcomes().stream()
                .anyMatch(outcome -> outcome.node().id().equals("node-b") && outcome.skipped()));
    }

    @Test
    void transientExecutionFailureIsRetried() {
        List<Integer> attempts = new ArrayList<>();
        DistributedLoadOrchestrator orchestrator = new DistributedLoadOrchestrator(
                (node, command) -> {
                    attempts.add(1);
                    if (attempts.size() == 1) {
                        throw new IllegalStateException("connection reset");
                    }
                    return DistributedLoadOrchestrator.Execution.fromExitCode(0, "ok");
                },
                node -> true,
                2);

        DistributedLoadOrchestrator.Dispatch dispatch = orchestrator.dispatch(plan, scenario);

        assertTrue(dispatch.passed());
        assertFalse(dispatch.degraded());
        assertEquals(2, dispatch.outcomes().get(0).attempts());
    }

    @Test
    void nodeThatKeepsThrowingIsDegradedInsteadOfFailingTheRun() {
        DistributedLoadOrchestrator orchestrator = new DistributedLoadOrchestrator(
                (node, command) -> {
                    if (node.id().equals("node-b")) {
                        throw new IllegalStateException("ssh timeout");
                    }
                    return DistributedLoadOrchestrator.Execution.fromExitCode(0, "ok");
                },
                node -> true,
                2);

        DistributedLoadOrchestrator.Dispatch dispatch = orchestrator.dispatch(plan, scenario);

        assertTrue(dispatch.passed(), "单节点故障不应把整体判成性能破线");
        assertTrue(dispatch.degraded());
        assertEquals(List.of("node-b"), dispatch.skippedNodes());
        assertTrue(dispatch.outcomes().stream()
                .anyMatch(outcome -> outcome.node().id().equals("node-b") && outcome.skipped()));
    }

    @Test
    void allNodesUnhealthyNeverPretendsToPass() {
        DistributedLoadOrchestrator orchestrator = new DistributedLoadOrchestrator(
                (node, command) -> DistributedLoadOrchestrator.Execution.fromExitCode(0, "ok"),
                node -> false,
                2);

        DistributedLoadOrchestrator.Dispatch dispatch = orchestrator.dispatch(plan, scenario);

        assertFalse(dispatch.passed());
        assertTrue(dispatch.degraded());
        assertEquals(2, dispatch.skippedNodes().size());
    }

    @Test
    void explicitVerdictFromANodeWinsOverExitCode() {
        LoadVerdict failing = new LoadVerdict(false, 50, List.of("并发 50：稳态 TPS 19.00 < 19.5"), List.of());
        DistributedLoadOrchestrator orchestrator = new DistributedLoadOrchestrator((node, command) ->
                new DistributedLoadOrchestrator.Execution(0, "parsed", failing));

        DistributedLoadOrchestrator.Dispatch dispatch = orchestrator.dispatch(plan, scenario);

        assertFalse(dispatch.passed());
        assertEquals(50, dispatch.aggregate().breakingConcurrency());
    }
}
