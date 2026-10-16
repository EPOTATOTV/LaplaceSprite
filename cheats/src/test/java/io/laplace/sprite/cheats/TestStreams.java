package io.laplace.sprite.cheats;

import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.model.NetworkModel;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.model.Scenario;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 测试用事件流与场景。 */
final class TestStreams {

    static final PlatformKey PLATFORM = PlatformKey.of(PlatformFamily.PAPER, "1.21.11");
    private static final String PLAYER_ID = "SimID-baseline";
    private static final long TICK_MILLIS = 50L;
    private static final int TICKS = 40;

    private TestStreams() {
    }

    /** 40 tick 的原版步行基线，含移动、数据包与 tick 事件。 */
    static EventStream walk() {
        InMemoryEventStream stream = new InMemoryEventStream();
        Instant origin = Instant.parse("2026-01-01T00:00:00Z");
        double x = 0.5;
        for (int tick = 0; tick < TICKS; tick++) {
            Instant timestamp = origin.plusMillis(tick * TICK_MILLIS);
            stream.emit(new Event.Move(PLAYER_ID, tick, timestamp, PLATFORM, x, 64.0, 0.5, 0f, 0f,
                    true, true, false));
            if (tick % 5 == 0) {
                stream.emit(new Event.Packet(PLAYER_ID, tick, timestamp, PLATFORM, "SERVERBOUND", "MOVE", 32));
            }
            stream.emit(new Event.Tick(PLAYER_ID, tick, timestamp, PLATFORM, 20.0, 3.5));
            x += 0.215;
        }
        return stream;
    }

    static Scenario scenario() {
        return new Scenario("pvp", "pvp", List.of(), NetworkModel.defaults().id(), 1, Map.of());
    }
}
