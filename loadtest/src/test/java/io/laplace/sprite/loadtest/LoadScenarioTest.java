package io.laplace.sprite.loadtest;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadScenarioTest {

    @Test
    void rampScheduleGeneratesSteps() {
        RampCurve ramp = new RampCurve(10, 10, 100, 30);

        List<RampCurve.RampStep> steps = ramp.schedule(300);

        assertEquals(3, steps.size());
        assertEquals(0, steps.get(0).startTick());
        assertEquals(100, steps.get(0).endTick());
        assertEquals(10, steps.get(0).concurrency());
        assertEquals(20, steps.get(1).concurrency());
        assertEquals(30, steps.get(2).concurrency());
        assertEquals(300, steps.get(2).endTick());
    }

    @Test
    void rampHoldsAtMaxUntilDurationEnds() {
        RampCurve ramp = new RampCurve(5, 5, 100, 10);

        List<RampCurve.RampStep> steps = ramp.schedule(350);

        assertEquals(List.of(5, 10, 10, 10),
                steps.stream().map(RampCurve.RampStep::concurrency).toList());
        assertEquals(350, steps.get(steps.size() - 1).endTick());
    }

    @Test
    void concurrencyAtFollowsSchedule() {
        RampCurve ramp = new RampCurve(10, 10, 100, 30);

        assertEquals(10, ramp.concurrencyAt(0, 300));
        assertEquals(10, ramp.concurrencyAt(99, 300));
        assertEquals(20, ramp.concurrencyAt(100, 300));
        assertEquals(30, ramp.concurrencyAt(299, 300));
        assertEquals(30, ramp.concurrencyAt(300, 300));
    }

    @Test
    void steadyStateWindowExcludesRampIn() {
        LoadScenario scenario = new LoadScenario("window", BotBackend.CARPET,
                List.of(BotAction.JOIN, BotAction.IDLE), 200, new RampCurve(10, 10, 100, 20), "");
        LoadTimeline timeline = new LoadTimeline(20);
        for (int tick = 0; tick < 200; tick++) {
            int concurrency = scenario.ramp().concurrencyAt(tick, 200);
            timeline.add(sample(tick, concurrency));
        }

        List<LoadTimeline.Window> windows = timeline.windows(scenario);

        assertEquals(2, windows.size());
        assertEquals(20, windows.get(0).startTick());
        assertEquals(100, windows.get(0).endTick());
        assertEquals(80, windows.get(0).samples().size());
        assertEquals(120, windows.get(1).startTick());
        assertEquals(80, windows.get(1).samples().size());
        assertTrue(windows.get(0).samples().stream().allMatch(sample -> sample.tick() >= 20));
        assertTrue(windows.get(0).samples().stream().allMatch(sample -> sample.tick() < 100));
    }

    @Test
    void stepShorterThanRampInHasEmptyWindow() {
        LoadScenario scenario = new LoadScenario("short", BotBackend.CARPET,
                List.of(BotAction.JOIN), 10, new RampCurve(10, 10, 10, 20), "");
        LoadTimeline timeline = new LoadTimeline(20);
        for (int tick = 0; tick < 10; tick++) {
            timeline.add(sample(tick, 10));
        }

        assertTrue(timeline.windows(scenario).get(0).empty());
    }

    @Test
    void parsesDeclarativeJson() {
        String json = """
                {
                  "id": "carpet-smoke",
                  "backend": "CARPET",
                  "actions": ["JOIN", "MINE"],
                  "durationTicks": 100,
                  "ramp": {"startConcurrency": 2, "step": 2, "stepTicks": 50, "maxConcurrency": 4},
                  "note": "冒烟"
                }
                """;

        LoadScenario parsed = new LoadScenarioParser().parse(json);

        assertEquals("carpet-smoke", parsed.id());
        assertEquals(BotBackend.CARPET, parsed.backend());
        assertEquals(List.of(BotAction.JOIN, BotAction.MINE), parsed.actions());
        assertEquals(4, parsed.maxConcurrency());
        assertEquals(2, parsed.schedule().size());
    }

    @Test
    void bundledScenariosCoverBothBackends() {
        List<LoadScenario> bundled = new LoadScenarioParser().bundled();

        assertEquals(2, bundled.size());
        Set<BotBackend> backends = bundled.stream().map(LoadScenario::backend).collect(Collectors.toSet());
        assertEquals(Set.of(BotBackend.MINEFLAYER, BotBackend.CARPET), backends);
        bundled.forEach(scenario -> assertFalse(scenario.note().isBlank()));
    }

    @Test
    void bundledTwentySixLoadsCoverEntitiesAndChunks() {
        List<LoadScenario> scenarios = new LoadScenarioParser().bundledTwentySixLoads();

        assertEquals(2, scenarios.size());
        assertEquals(List.of("entities-26.2", "chunks-26.2"),
                scenarios.stream().map(LoadScenario::id).toList());
        assertTrue(scenarios.get(0).actions().contains(BotAction.ENTITY_LOAD));
        assertTrue(scenarios.get(1).actions().contains(BotAction.CHUNK_LOAD));
        scenarios.forEach(scenario -> assertTrue(scenario.note().contains("26.2")));
    }

    private static LoadSample sample(int tick, int concurrency) {
        return new LoadSample(tick, concurrency, 20.0, 10.0, 12.0, 15.0, concurrency, 400, 20);
    }
}
