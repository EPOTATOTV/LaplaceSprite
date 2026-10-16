package io.laplace.sprite.network;

import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.model.BatchPattern;
import io.laplace.sprite.core.model.NetworkModel;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkConditionerTest {

    private static final PlatformKey PLATFORM = PlatformKey.of(PlatformFamily.PAPER, "1.21");
    private static final Instant BASE = Instant.ofEpochMilli(1_700_000_000_000L);
    private static final int SAMPLES = 10_000;
    private static final int PACKETS = 2;

    private final NetworkConditioner conditioner = new NetworkConditioner();

    @Test
    void latencyStaysWithinModelBounds() {
        NetworkModel model = NetworkModel.defaults();
        Random rng = new Random(1L);
        for (int i = 0; i < SAMPLES; i++) {
            int latency = conditioner.sampleLatencyMs(model, rng);
            assertTrue(latency >= model.latencyMinMs() && latency <= model.latencyMaxMs(),
                    "延迟越界: " + latency);
        }
    }

    @Test
    void latencyRespectsJitterAndSpikeCeiling() {
        NetworkModel model = NetworkModel.lte();
        int ceiling = model.latencyMaxMs() + model.jitterMs() + model.spikeMs();
        Random rng = new Random(2L);
        for (int i = 0; i < SAMPLES; i++) {
            int latency = conditioner.sampleLatencyMs(model, rng);
            assertTrue(latency >= model.latencyMinMs() && latency <= ceiling, "延迟越界: " + latency);
        }
    }

    @Test
    void dropRateApproximatesConfiguredLoss() {
        NetworkModel model = new NetworkModel("test-loss", 30, 60, 0, 0, 0.3, 0.0, BatchPattern.NONE,
                "1.21", 0L);
        Random rng = new Random(3L);
        int drops = 0;
        for (int i = 0; i < SAMPLES; i++) {
            if (conditioner.drop(model, rng)) {
                drops++;
            }
        }
        assertEquals(0.3, (double) drops / SAMPLES, 0.02);
    }

    @Test
    void reorderRateApproximatesConfiguredRate() {
        NetworkModel model = new NetworkModel("test-reorder", 30, 60, 0, 0, 0.0, 0.25, BatchPattern.NONE,
                "1.21", 0L);
        Random rng = new Random(4L);
        int reorders = 0;
        for (int i = 0; i < SAMPLES; i++) {
            if (conditioner.reorder(model, rng)) {
                reorders++;
            }
        }
        assertEquals(0.25, (double) reorders / SAMPLES, 0.02);
    }

    @Test
    void zeroLossAndZeroReorderPreserveEventCount() {
        EventStream in = stream();
        EventStream conditioned = conditioner.condition(NetworkModel.defaults(), in, 42L);
        assertEquals(in.size(), conditioned.size());
    }

    @Test
    void lossDropsOnlyPacketEvents() {
        EventStream in = stream();
        NetworkModel allLoss = new NetworkModel("all-loss", 0, 0, 0, 0, 1.0, 0.0, BatchPattern.NONE, "1.21",
                0L);
        List<Event> conditioned = conditioner.condition(allLoss, in, 5L).events();
        assertEquals(in.size() - PACKETS, conditioned.size());
        assertTrue(conditioned.stream().noneMatch(Event.Packet.class::isInstance));
    }

    @Test
    void latencyShiftsTimestampAndKeepsTickAndPlatform() {
        EventStream in = stream();
        NetworkModel fixed = new NetworkModel("fixed", 100, 100, 0, 0, 0.0, 0.0, BatchPattern.NONE, "1.21",
                0L);
        List<Event> original = in.events();
        List<Event> conditioned = conditioner.condition(fixed, in, 7L).events();
        assertEquals(original.size(), conditioned.size());
        for (int i = 0; i < conditioned.size(); i++) {
            assertEquals(original.get(i).timestamp().plusMillis(100), conditioned.get(i).timestamp());
            assertEquals(original.get(i).tick(), conditioned.get(i).tick());
            assertEquals(PLATFORM, conditioned.get(i).platform());
        }
    }

    @Test
    void conditionIsDeterministicForSameSeed() {
        NetworkModel model = NetworkModel.lte();
        assertEquals(conditioner.condition(model, stream(), 11L).events(),
                conditioner.condition(model, stream(), 11L).events());
    }

    @Test
    void batchingNotesDescribeEachPattern() {
        assertTrue(conditioner.batchingNotes(NetworkModel.wifi()).stream().anyMatch(note -> note.contains("wifi")));
        assertTrue(conditioner.batchingNotes(NetworkModel.lte()).stream().anyMatch(note -> note.contains("lte")));
        assertTrue(conditioner.batchingNotes(NetworkModel.defaults()).stream()
                .anyMatch(note -> note.contains("无批处理")));
    }

    private static EventStream stream() {
        List<Event> events = new ArrayList<>();
        for (int tick = 0; tick < 3; tick++) {
            events.add(new Event.Move("legit-0", tick, BASE.plusMillis(tick * 50L), PLATFORM,
                    tick * 0.2, 64.0, 0.0, 90.0f, 0.0f, true, true, false));
        }
        for (int i = 0; i < PACKETS; i++) {
            events.add(new Event.Packet("legit-0", 10 + i, BASE.plusMillis(500L + i * 50L), PLATFORM,
                    "client->server", "player_position", 32));
        }
        events.add(new Event.Tick("legit-0", 20, BASE.plusMillis(1000L), PLATFORM, 20.0, 3.0));
        return new InMemoryEventStream(events);
    }
}
