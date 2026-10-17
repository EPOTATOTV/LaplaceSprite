package io.laplace.sprite.core.engine.replay;

import com.fasterxml.jackson.databind.JsonNode;

import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.event.EventType;
import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.core.model.CheatType;
import io.laplace.sprite.core.model.FlagEvent;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.model.SampleTierName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 4.6 事件流回放：确定性、闭区间、断点分段、单步与导出回读。 */
class EventReplaySessionTest {

    private static final PlatformKey FABRIC = PlatformKey.of(PlatformFamily.FABRIC, "1.21.1");
    private static final Instant ORIGIN = Instant.parse("2026-10-13T00:00:00Z");

    @Test
    void replayIsDeterministicAndSortedByTick() {
        EventReplaySession session = session();

        assertEquals(List.of(1L, 2L, 2L, 3L, 4L), ticks(session.replay()));
        assertEquals(ticks(session.replay()), ticks(session.replay()), "重复回放顺序必须一致");
        assertEquals(List.of(EventType.MOVE, EventType.MOVE, EventType.TICK, EventType.MOVE,
                EventType.MOVE), session.replay().stream().map(Event::type).toList());
    }

    @Test
    void rangeSlicingIsInclusiveAtBothEnds() {
        EventReplaySession session = session();

        assertEquals(List.of(2L, 2L), ticks(session.replayRange(2, 2)));
        assertEquals(List.of(1L, 2L, 2L, 3L, 4L), ticks(session.replayRange(1, 4)));
        assertTrue(session.replayRange(5, 9).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> session.replayRange(3, 2));
    }

    @Test
    void breakpointAtTickSplitsSegmentsExactlyThere() {
        EventReplaySession session = session().withBreakpoint(Breakpoint.at(2, "命中"));

        List<EventReplaySession.Segment> segments = session.segments();

        assertEquals(2, segments.size());
        assertEquals(1, segments.get(0).fromTick());
        assertEquals(1, segments.get(0).toTick());
        assertNull(segments.get(0).breakpointLabel());
        assertEquals(List.of(1L), ticks(segments.get(0).events()));

        assertEquals(2, segments.get(1).fromTick());
        assertEquals(4, segments.get(1).toTick());
        assertEquals("命中", segments.get(1).breakpointLabel());
        assertEquals(List.of(2L, 2L, 3L, 4L), ticks(segments.get(1).events()));
    }

    @Test
    void multipleBreakpointsProduceMultipleSegments() {
        EventReplaySession session = session()
                .withBreakpoint(Breakpoint.at(2, "第一段"))
                .withBreakpoint(Breakpoint.at(4, "第二段"));

        List<EventReplaySession.Segment> segments = session.segments();

        assertEquals(3, segments.size());
        assertEquals(List.of(1L), ticks(segments.get(0).events()));
        assertEquals(List.of(2L, 2L, 3L), ticks(segments.get(1).events()));
        assertEquals(List.of(4L), ticks(segments.get(2).events()));
        assertEquals("第一段", segments.get(1).breakpointLabel());
        assertEquals("第二段", segments.get(2).breakpointLabel());
    }

    @Test
    void steppingPastTheEndIsSafeAndResetRewinds() {
        EventReplaySession session = session();

        for (int i = 0; i < 5; i++) {
            assertTrue(session.step().isPresent());
        }
        assertTrue(session.step().isEmpty(), "越过末尾返回空而不是抛异常");
        assertEquals(5, session.cursor());

        session.reset();
        assertEquals(0, session.cursor());
        assertEquals(1L, session.step().orElseThrow().tick());
    }

    @Test
    void exportJsonRoundTripsIntoSessionWithSameCountAndOrder(@TempDir Path directory) throws IOException {
        EventReplaySession session = session().withBreakpoint(Breakpoint.at(3, "导出段"));

        ReplayExport export = session.export(directory);

        assertTrue(export.jsonPath().toFile().isFile());
        assertTrue(export.markdownPath().toFile().isFile());
        assertEquals(5, export.eventCount());

        JsonNode root = Json.jsonMapper().readTree(export.json());
        assertEquals(session.flag().id(), root.get("flagId").asText());
        assertEquals("run-1", root.get("runId").asText());
        assertEquals("p1", root.get("playerId").asText());
        assertEquals("sim.speed", root.get("checkName").asText());
        assertEquals(2, root.get("tick").asLong());
        assertEquals(42L, root.get("seed").asLong());
        assertEquals("traj-1", root.get("trajectoryRef").asText());
        assertEquals(5, root.get("events").size());

        EventReplaySession restored = EventReplaySession.read(export.jsonPath());

        assertEquals(session.eventCount(), restored.eventCount());
        assertEquals(ticks(session.replay()), ticks(restored.replay()));
        assertEquals(session.replay().stream().map(Event::type).toList(),
                restored.replay().stream().map(Event::type).toList());
        assertEquals(42L, restored.seed());
        assertTrue(restored.breakpoints().isEmpty(), "断点不随导出保存");
        assertFalse(export.markdown().isBlank());
        assertTrue(export.markdown().contains("## 断点"));
    }

    private static EventReplaySession session() {
        InMemoryEventStream stream = new InMemoryEventStream();
        stream.emit(move(3));
        stream.emit(move(1));
        stream.emit(move(2));
        stream.emit(new Event.Tick("p1", 2, ORIGIN.plusMillis(100), FABRIC, 20.0, 12.0));
        stream.emit(move(4));
        FlagEvent flag = FlagEvent.truePositive("run-1", "p1", "sim.speed", CheatType.SPEED,
                SampleTierName.DEFAULT, 0.99, 2, "traj-1", null);
        return EventReplaySession.bind(flag, stream, 42L);
    }

    private static Event.Move move(long tick) {
        return new Event.Move("p1", tick, ORIGIN.plusMillis(tick * 50L), FABRIC, tick, 64.0, 0.5,
                0f, 0f, true, false, false);
    }

    private static List<Long> ticks(List<Event> events) {
        return events.stream().map(Event::tick).toList();
    }
}
