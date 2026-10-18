package io.laplace.sprite.loadtest;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 分布式压测编排（L-07）：并发拆分与结果聚合。 */
class DistributedLoadPlanTest {

    private final LoadScenario scenario = new LoadScenario("distributed", BotBackend.MINEFLAYER,
            List.of(BotAction.JOIN, BotAction.CHUNK_LOAD), 600,
            new RampCurve(10, 10, 100, 100), "");

    @Test
    void botsAreSplitEvenlyAndSumToTheScenarioPeak() {
        DistributedLoadPlan plan = DistributedLoadPlan.split(scenario, List.of(
                new DistributedLoadPlan.Node("node-a", DistributedLoadPlan.NodeTransport.SSH, "10.0.0.1", 1),
                new DistributedLoadPlan.Node("node-b", DistributedLoadPlan.NodeTransport.DOCKER, "10.0.0.2", 1),
                new DistributedLoadPlan.Node("node-c", DistributedLoadPlan.NodeTransport.SSH, "10.0.0.3", 1)));

        assertEquals(100, plan.totalBots());
        assertEquals(List.of(34, 33, 33), plan.nodes().stream()
                .map(DistributedLoadPlan.Node::bots).toList());
        assertEquals(100, plan.botsByNode().values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void moreNodesThanBotsIsRejected() {
        List<DistributedLoadPlan.Node> nodes = List.of(
                new DistributedLoadPlan.Node("a", DistributedLoadPlan.NodeTransport.SSH, "h", 1),
                new DistributedLoadPlan.Node("b", DistributedLoadPlan.NodeTransport.SSH, "h", 1));
        LoadScenario small = new LoadScenario("tiny", BotBackend.CARPET, List.of(BotAction.JOIN), 100,
                RampCurve.single(1), "");

        assertThrows(IllegalArgumentException.class, () -> DistributedLoadPlan.split(small, nodes));
    }

    @Test
    void anyFailingNodeFailsTheAggregate() {
        Map<String, LoadVerdict> verdicts = new LinkedHashMap<>();
        verdicts.put("node-a", pass());
        verdicts.put("node-b", new LoadVerdict(false, 40, List.of("并发 40：稳态 TPS 19.00 < 19.5"), List.of()));

        DistributedLoadPlan.AggregatedVerdict aggregate = DistributedLoadPlan.aggregate(verdicts);

        assertFalse(aggregate.passed());
        assertEquals(40, aggregate.breakingConcurrency());
        assertEquals(2, aggregate.nodes());
        assertTrue(aggregate.violations().get(0).startsWith("node-b："));
    }

    @Test
    void aggregatePassesOnlyWhenEveryNodePasses() {
        Map<String, LoadVerdict> verdicts = Map.of("node-a", pass(), "node-b", pass());

        DistributedLoadPlan.AggregatedVerdict aggregate = DistributedLoadPlan.aggregate(verdicts);

        assertTrue(aggregate.passed());
        assertNull(aggregate.breakingConcurrency());
        assertTrue(aggregate.violations().isEmpty());
    }

    @Test
    void missingNodeVerdictIsAFailureRatherThanSilence() {
        Map<String, LoadVerdict> verdicts = new LinkedHashMap<>();
        verdicts.put("node-a", pass());
        verdicts.put("node-b", null);

        DistributedLoadPlan.AggregatedVerdict aggregate = DistributedLoadPlan.aggregate(verdicts);

        assertFalse(aggregate.passed());
        assertTrue(aggregate.violations().contains("node-b：缺少判定结果"));
    }

    private static LoadVerdict pass() {
        return new LoadVerdict(true, null, List.of(), List.of());
    }
}
