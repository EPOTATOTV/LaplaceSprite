package io.laplace.sprite.loadtest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadGateTest {

    private final LoadGate gate = new LoadGate();

    @Test
    void exactlyMinimumTpsPasses() {
        LoadScenario scenario = singleConcurrencyScenario(100);

        LoadVerdict verdict = gate.evaluate(scenario, timeline(19.5, 50.0, 100));

        assertTrue(verdict.passed());
        assertNull(verdict.breakingConcurrency());
        assertTrue(verdict.violations().isEmpty());
    }

    @Test
    void tpsJustBelowMinimumFails() {
        LoadScenario scenario = singleConcurrencyScenario(100);

        LoadVerdict verdict = gate.evaluate(scenario, timeline(19.49, 50.0, 100));

        assertFalse(verdict.passed());
        assertEquals(1, verdict.breakingConcurrency());
        assertTrue(verdict.violations().stream().anyMatch(text -> text.contains("19.49")));
        assertTrue(verdict.violations().stream().anyMatch(text -> text.contains("19.5")));
    }

    @Test
    void exactlyMaximumP95Passes() {
        LoadScenario scenario = singleConcurrencyScenario(100);

        LoadVerdict verdict = gate.evaluate(scenario, timeline(19.5, 50.0, 100));

        assertTrue(verdict.passed());
    }

    @Test
    void p95JustAboveMaximumFails() {
        LoadScenario scenario = singleConcurrencyScenario(100);

        LoadVerdict verdict = gate.evaluate(scenario, timeline(19.5, 50.01, 100));

        assertFalse(verdict.passed());
        assertEquals(1, verdict.breakingConcurrency());
        assertTrue(verdict.violations().stream().anyMatch(text -> text.contains("50.01")));
        assertTrue(verdict.violations().stream().anyMatch(text -> text.contains("50.0")));
    }

    @Test
    void breakingConcurrencyIsFirstFailingStep() {
        LoadScenario scenario = new LoadScenario("ramp", BotBackend.MINEFLAYER,
                List.of(BotAction.JOIN, BotAction.WALK), 300, new RampCurve(10, 10, 100, 30), "");
        LoadTimeline timeline = new LoadTimeline(20);
        for (int tick = 0; tick < 300; tick++) {
            int concurrency = scenario.ramp().concurrencyAt(tick, 300);
            double tps = concurrency == 30 ? 19.0 : 20.0;
            timeline.add(sample(tick, concurrency, tps, 10.0));
        }

        LoadVerdict verdict = gate.evaluate(scenario, timeline);

        assertFalse(verdict.passed());
        assertEquals(30, verdict.breakingConcurrency());
        assertEquals(List.of(10, 20, 30), verdict.curvePoints().stream()
                .map(LoadVerdict.CurvePoint::concurrency).toList());
        assertEquals(3, verdict.curvePoints().size());
        assertFalse(verdict.curvePoints().get(2).passed());
    }

    @Test
    void emptyTimelineDoesNotPass() {
        LoadScenario scenario = singleConcurrencyScenario(100);

        LoadVerdict verdict = gate.evaluate(scenario, new LoadTimeline(20));

        assertFalse(verdict.passed());
        assertNull(verdict.breakingConcurrency());
        assertTrue(verdict.violations().stream().anyMatch(text -> text.contains("没有可判定")));
    }

    @Test
    void singlePointGateIsInclusiveAtBothBoundaries() {
        assertTrue(LoadGate.passes(19.5, 50.0));
        assertFalse(LoadGate.passes(19.49, 50.0));
        assertTrue(LoadGate.passes(19.5, 50.0));
        assertFalse(LoadGate.passes(19.5, 50.01));
    }

    private static LoadScenario singleConcurrencyScenario(int durationTicks) {
        return new LoadScenario("single", BotBackend.CARPET, List.of(BotAction.JOIN, BotAction.IDLE),
                durationTicks, RampCurve.single(1), "");
    }

    private static LoadTimeline timeline(double tps, double msptP95, int durationTicks) {
        LoadTimeline timeline = new LoadTimeline(20);
        for (int tick = 0; tick < durationTicks; tick++) {
            timeline.add(sample(tick, 1, tps, msptP95));
        }
        return timeline;
    }

    private static LoadSample sample(int tick, int concurrency, double tps, double msptP95) {
        double p50 = msptP95 / 2.0;
        return new LoadSample(tick, concurrency, tps, p50, msptP95, msptP95, concurrency, 400, 20);
    }
}
