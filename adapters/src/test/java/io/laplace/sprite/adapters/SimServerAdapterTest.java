package io.laplace.sprite.adapters;

import io.laplace.sprite.adapters.sim.HeuristicDetectionEngine;
import io.laplace.sprite.adapters.sim.SimAdapter;
import io.laplace.sprite.core.engine.DetectionContext;
import io.laplace.sprite.core.engine.DetectionTarget;
import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.model.CheatTierName;
import io.laplace.sprite.core.model.FlagEvent;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.model.PlayerType;
import io.laplace.sprite.core.model.ProbeSnapshot;
import io.laplace.sprite.core.model.Scenario;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SimAdapter 启发表（4.4）：合法基线零 flag，注入流命中对应检测项。 */
class SimAdapterTest {

    private static final PlatformKey PAPER = PlatformKey.of(PlatformFamily.PAPER, "1.21.11");
    private static final String PLAYER = AdapterTestSupport.PLAYER;

    private final SimAdapter adapter = new SimAdapter();

    @Test
    void legitWalkBaselineProducesZeroFlags() {
        assertTrue(detect(AdapterTestSupport.legitBaseline(PAPER)).isEmpty());
    }

    @Test
    void legitSprintAtModelLimitProducesZeroFlags() {
        // 模拟器疾跑模型是 5.6 格/秒 ≈ 0.28 格/tick，必须落在阈值内。
        assertTrue(detect(AdapterTestSupport.moveStream(PAPER, 60, 0.28, true, 0.0, true)).isEmpty());
    }

    @Test
    void detectsSpeed() {
        List<FlagEvent> flags = detect(AdapterTestSupport.moveStream(PAPER, 40, 0.9, true, 0.0, true));
        assertHasCheck(flags, HeuristicDetectionEngine.CHECK_SPEED);
        assertNoCheck(flags, HeuristicDetectionEngine.CHECK_BLINK);
    }

    @Test
    void detectsFly() {
        List<FlagEvent> flags = detect(AdapterTestSupport.moveStream(PAPER, 40, 0.1, false, 0.5, false));
        assertHasCheck(flags, HeuristicDetectionEngine.CHECK_FLY);
    }

    @Test
    void detectsBlink() {
        InMemoryEventStream stream = new InMemoryEventStream();
        double x = 0.5;
        Instant origin = AdapterTestSupport.ORIGIN;
        for (int tick = 0; tick < 12; tick++) {
            stream.emit(new Event.Move(PLAYER, tick, origin.plusMillis(tick * 50L), PAPER, x, 64.0, 0.5,
                    0f, 0f, true, true, false));
            x += 0.2;
        }
        // 一次性跳变 4 格
        stream.emit(new Event.Move(PLAYER, 12, origin.plusMillis(600L), PAPER, x + 4.0, 64.0, 0.5,
                0f, 0f, true, true, false));
        for (int tick = 13; tick < 24; tick++) {
            stream.emit(new Event.Move(PLAYER, tick, origin.plusMillis(tick * 50L), PAPER, x + 4.0, 64.0, 0.5,
                    0f, 0f, true, true, false));
        }
        assertHasCheck(detect(stream), HeuristicDetectionEngine.CHECK_BLINK);
    }

    @Test
    void detectsReach() {
        InMemoryEventStream stream = AdapterTestSupport.legitBaseline(PAPER);
        stream.emit(new Event.Combat(PLAYER, 40, AdapterTestSupport.ORIGIN.plusMillis(2000L), PAPER,
                "target-1", 4.5, 10, "reach"));
        assertHasCheck(detect(stream), HeuristicDetectionEngine.CHECK_REACH);
    }

    @Test
    void detectsAutoClicker() {
        InMemoryEventStream stream = new InMemoryEventStream();
        for (int tick = 0; tick < 20; tick++) {
            stream.emit(new Event.Combat(PLAYER, tick, AdapterTestSupport.ORIGIN.plusMillis(tick * 50L),
                    PAPER, "target-1", 2.9, 16, "auto"));
        }
        assertHasCheck(detect(stream), HeuristicDetectionEngine.CHECK_AUTO_CLICKER);
    }

    @Test
    void detectsScaffold() {
        InMemoryEventStream stream = new InMemoryEventStream();
        double x = 0.5;
        for (int tick = 0; tick < 20; tick++) {
            Instant at = AdapterTestSupport.ORIGIN.plusMillis(tick * 50L);
            stream.emit(new Event.Move(PLAYER, tick, at, PAPER, x, 64.0, 0.5, 0f, 0f, true, true, false));
            int bx = (int) Math.floor(x);
            stream.emit(new Event.Interact(PLAYER, tick, at, PAPER, "minecraft:obsidian", bx, 63, 0, "place"));
            stream.emit(new Event.BlockChange(PLAYER, tick, at, PAPER, bx, 63, 0, "minecraft:air",
                    "minecraft:obsidian"));
            x += 0.215;
        }
        List<FlagEvent> flags = detect(stream);
        assertHasCheck(flags, HeuristicDetectionEngine.CHECK_SCAFFOLD);
        assertNoCheck(flags, HeuristicDetectionEngine.CHECK_SPEED);
    }

    @Test
    void detectionTargetEdgesAreWired() {
        DetectionTarget target = new SimAdapter(new HeuristicDetectionEngine());
        assertTrue(target.id().contains("sim"));
        assertTrue(target.detect(ctx(AdapterTestSupport.legitBaseline(PAPER))).isEmpty());
    }

    private List<FlagEvent> detect(EventStream stream) {
        return adapter.detect(ctx(stream));
    }

    private static DetectionContext ctx(EventStream stream) {
        Scenario scenario = new Scenario("pvp", "pvp", List.of(), "default", 1, Map.of());
        return new DetectionContext("run-sim", PAPER, scenario, stream, ProbeSnapshot.clean(), PLAYER,
                PlayerType.CHEAT, null, CheatTierName.DEFAULT);
    }

    private static void assertHasCheck(List<FlagEvent> flags, String check) {
        assertTrue(flags.stream().anyMatch(flag -> flag.checkName().equals(check)),
                "缺少检测项 %s，实际 %s".formatted(check, flags.stream().map(FlagEvent::checkName).toList()));
    }

    private static void assertNoCheck(List<FlagEvent> flags, String check) {
        assertFalse(flags.stream().anyMatch(flag -> flag.checkName().equals(check)),
                "不应出现检测项 %s".formatted(check));
    }
}
