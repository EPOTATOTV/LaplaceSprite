package io.laplace.sprite.servers;

import io.laplace.sprite.core.model.MetricSample;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 指标采集（12.3）：离线合成序列、拐点判定、起停。 */
class MetricsAgentTest {

    @Test
    void steadyProfileNeverBreaches() {
        MetricsAgent agent = MetricsAgent.synthetic("steady", SyntheticProfile.steady());
        for (int i = 0; i < 20; i++) {
            agent.sampleNow();
        }
        assertEquals(20, agent.sampleCount());
        assertTrue(agent.kneePoints().isEmpty());
        assertSame(agent.history().get(19), agent.current());
    }

    @Test
    void msptRampBreachesAtFifthStep() {
        MetricsAgent agent = MetricsAgent.synthetic("ramp", SyntheticProfile.msptRamp());
        for (int i = 0; i < 10; i++) {
            agent.sampleNow();
        }
        List<MetricSample> knees = agent.kneePoints();
        assertFalse(knees.isEmpty(), "MSPT 斜坡必须产生拐点");
        assertTrue(knees.get(0).mspt() > 50.0, "拐点首条 MSTP = " + knees.get(0).mspt());
        assertEquals(agent.history().get(5), knees.get(0), "拐点应落在第 5 次采样");
    }

    @Test
    void tpsBreachAlsoCountsAsKnee() {
        try (MetricsAgent agent = new MetricsAgent("tps", 5000L,
                step -> new MetricSample(java.time.Instant.parse("2026-01-01T00:00:00Z"), 19.4 - step, 12.0,
                        256, 4096, 2.0, 20.0, 30.0, 0, 100))) {
            agent.sampleNow();
            assertEquals(1, agent.kneePoints().size());
        }
    }

    @Test
    void startAndStopToggleRunningFlag() {
        MetricsAgent agent = MetricsAgent.synthetic("lifecycle", 10L, SyntheticProfile.steady());
        assertFalse(agent.running());
        agent.start();
        assertTrue(agent.running());
        agent.stop();
        assertFalse(agent.running());
        assertEquals(5000L, MetricsAgent.synthetic("d", SyntheticProfile.steady()).intervalMs());
        assertEquals(10L, agent.intervalMs());
    }

    @Test
    void intervalIsConfigurable() {
        MetricsAgent agent = MetricsAgent.synthetic("interval", 250L, SyntheticProfile.steady());
        assertEquals(250L, agent.intervalMs());
        assertEquals("interval", agent.instanceId());
    }
}
