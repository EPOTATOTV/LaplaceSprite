package io.laplace.sprite.servers;

import io.laplace.sprite.core.model.MetricSample;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 阶梯负载（12.3 ②）：25 / 50 / 100 找第一个拐点。 */
class LoadStaircaseTest {

    private static final Instant ORIGIN = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void planHasDefaultSteps() {
        LoadStaircase staircase = new LoadStaircase();
        assertEquals(List.of(25, 50, 100), staircase.playerSteps());
        List<LoadStage> plan = staircase.plan();
        assertEquals(3, plan.size());
        assertEquals(25, plan.get(0).simulatedPlayers());
        assertTrue(plan.stream().noneMatch(LoadStage::breached), "未测量的台阶不算拐点");
        assertFalse(staircase.measuredStages().stream().findAny().isPresent());
    }

    @Test
    void kneeIsTheFirstBreachingStage() {
        LoadStaircase staircase = new LoadStaircase();
        staircase.record(25, sample(20.0, 30.0, 0));
        assertTrue(staircase.knee().isEmpty());
        staircase.record(50, sample(20.0, 55.0, 1));
        assertEquals(50, staircase.knee().orElseThrow().simulatedPlayers());
        staircase.record(100, sample(12.0, 90.0, 2));
        assertEquals(50, staircase.knee().orElseThrow().simulatedPlayers(), "拐点取第一个越界台阶");
        assertTrue(staircase.completed());
        assertTrue(staircase.hasKnee());
    }

    @Test
    void tpsDropAlsoCountsAsKnee() {
        LoadStaircase staircase = new LoadStaircase();
        staircase.record(25, sample(19.2, 30.0, 0));
        assertEquals(25, staircase.knee().orElseThrow().simulatedPlayers());
    }

    @Test
    void planMixesMeasuredAndPlannedStages() {
        LoadStaircase staircase = new LoadStaircase();
        staircase.record(25, sample(20.0, 30.0, 0));
        List<LoadStage> plan = staircase.plan();
        assertTrue(plan.get(0).measured());
        assertFalse(plan.get(1).measured());
    }

    private static MetricSample sample(double tps, double mspt, int step) {
        return new MetricSample(ORIGIN.plusSeconds(step * 30L), tps, mspt, 1024, 4096, 4.0, 40.0, 35.0,
                step * 25, 400);
    }
}
