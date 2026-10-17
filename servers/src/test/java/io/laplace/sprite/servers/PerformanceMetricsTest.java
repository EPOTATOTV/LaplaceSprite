package io.laplace.sprite.servers;

import io.laplace.sprite.core.model.MetricSample;
import io.laplace.sprite.core.model.NetworkLatencyDistribution;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.model.ServerHealth;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 12.3 连接 / 带宽 / 区块 / 实体 / 网络延迟进入采样与实例健康。 */
class PerformanceMetricsTest {

    private static final PlatformKey PAPER = PlatformKey.of(PlatformFamily.PAPER, "1.21.11");

    @Test
    void syntheticProfileCarriesLoadAndLatency() {
        MetricSample steady = SyntheticProfile.steady().sample(0, Instant.EPOCH);
        assertEquals(0, steady.connections());
        assertEquals(3200.0, steady.bandwidthKbps(), 1e-9);
        assertEquals(400, steady.chunksLoaded());
        assertEquals(2400, steady.entities());
        assertEquals(28.0, steady.pingP50Ms(), 1e-9);
        assertEquals(96.0, steady.pingP95Ms(), 1e-9);

        MetricSample second = SyntheticProfile.steady().sample(2, Instant.EPOCH);
        assertEquals(2, second.connections());
        assertEquals(3232.0, second.bandwidthKbps(), 1e-9, "每步带宽 +16Kbps");
        assertEquals(406, second.chunksLoaded());
        assertEquals(2436, second.entities());

        MetricSample loaded = SyntheticProfile.loadRamp(100).sample(0, Instant.EPOCH);
        assertEquals(100, loaded.connections());
        assertEquals(4400.0, loaded.bandwidthKbps(), 1e-9);
        assertEquals(1400, loaded.chunksLoaded());
        assertEquals(8400, loaded.entities());
        assertEquals(28.5, loaded.pingP50Ms(), 1e-9);
        assertEquals(110.0, loaded.pingP95Ms(), 1e-9);
    }

    @Test
    void metricsAgentReportsWorstLatencyWindow() {
        MetricsAgent agent = MetricsAgent.synthetic("steady", SyntheticProfile.steady());
        agent.sampleNow();
        agent.sampleNow();
        agent.sampleNow();

        NetworkLatencyDistribution distribution = agent.latencyDistribution();

        assertTrue(distribution.measured());
        assertEquals(3, distribution.sampleCount());
        assertEquals(28.0, distribution.p50Ms(), 1e-9);
        assertEquals(96.0, distribution.p95Ms(), 1e-9);
    }

    @Test
    void legacySamplesDoNotPretendToHaveLatency() {
        MetricsAgent agent = new MetricsAgent("legacy", 5000L,
                step -> new MetricSample(Instant.EPOCH, 20.0, 12.0, 512, 4096, 4.0, 25.0, 30.0,
                        8, 320));
        agent.sampleNow();

        assertFalse(agent.current().hasLatencySample());
        assertFalse(agent.latencyDistribution().measured());
    }

    @Test
    void serverHealthExposesConnectionsBandwidthChunksAndEntities() {
        TestServerOrchestrator orchestrator = new TestServerOrchestrator(new InProcessDriver());
        orchestrator.startAll(List.of(TestServerSpec.inProcess("metrics", PAPER, "1.0.0")));

        ServerHealth health = orchestrator.health().get(0);

        assertEquals(0, health.connections());
        assertEquals(3200.0, health.bandwidthKbps(), 1e-9);
        assertEquals(400, health.chunksLoaded());
        assertEquals(2400, health.entities());
        assertEquals(28.0, health.pingP50Ms(), 1e-9);
        assertEquals(96.0, health.pingP95Ms(), 1e-9);
        assertTrue(health.healthy());
    }
}
