package io.laplace.sprite.report.console;

import io.laplace.sprite.core.model.SampleTierName;
import io.laplace.sprite.core.model.CheatType;
import io.laplace.sprite.core.model.DetectionBreakdown;
import io.laplace.sprite.core.model.LiveViewGrid;
import io.laplace.sprite.core.model.MetricSet;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.model.RunStatus;
import io.laplace.sprite.core.model.ServerPluginConfig;
import io.laplace.sprite.core.model.TestRun;
import io.laplace.sprite.core.model.Thresholds;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ConsoleDataBuilderTest {

    private final ConsoleDataBuilder builder = new ConsoleDataBuilder();

    @Test
    void cardsAndRunsAreConsistentWithInput() {
        List<TestRun> runs = List.of(run("run-1", 100, 0, 20, 20), run("run-2", 50, 1, 10, 10));

        ConsoleData data = builder.build(runs, List.of(), ServerPluginConfig.safeDefaults(),
                LiveViewGrid.empty(), List.of(), List.of(), Thresholds.defaults(), 15, 93);

        assertEquals(runs.size(), data.cards().todayRuns());
        assertEquals(runs.size(), data.runs().size());
        assertEquals(150, data.cards().simulatedPlayers());
        assertEquals(15, data.cards().cheatSpecCount());
        assertEquals(93, data.fingerprintCount());
        assertEquals(1.0 / 150.0, data.cards().falsePositiveRate(), 1e-9);
        assertEquals(1.0, data.cards().detectionRate(), 1e-9);
        assertEquals(0, data.cards().gatePassed(), "误报率超限时门禁应为不通过");
        assertEquals(2, data.falsePositiveHistory().points().size());
        assertEquals(2, data.detectionHistory().points().size());
        assertFalse(builder.toJson(data).isBlank());
    }

    private static TestRun run(String id, int legit, int falsePositives, int cheat, int detected) {
        MetricSet metrics = new MetricSet(legit, falsePositives, cheat, detected, 0, 0, true, 0.75);
        DetectionBreakdown breakdown = new DetectionBreakdown(new LinkedHashMap<>(),
                new EnumMap<>(SampleTierName.class), new EnumMap<>(CheatType.class), new EnumMap<>(CheatType.class));
        return new TestRun(id, "detection-engine", "cheat",
                PlatformKey.of(PlatformFamily.PAPER, "1.20.4"), metrics, breakdown,
                List.of(), List.of(), RunStatus.PASSED, 1L, Instant.now(), 100L);
    }
}
