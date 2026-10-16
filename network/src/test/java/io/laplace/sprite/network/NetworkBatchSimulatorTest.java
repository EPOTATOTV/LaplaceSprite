package io.laplace.sprite.network;

import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.NetworkModel;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkBatchSimulatorTest {

    private static final PlatformKey PLATFORM = PlatformKey.of(PlatformFamily.PAPER, "1.21");
    private static final Instant BASE = Instant.ofEpochMilli(1_700_000_000_000L);

    private final NetworkBatchSimulator simulator = new NetworkBatchSimulator();

    @Test
    void nonePatternKeepsPacketsUntouched() {
        List<Event> packets = packets(10);
        assertEquals(packets, simulator.batch(packets, NetworkModel.defaults(), 1L));
    }

    @Test
    void wifiBatchingCollapsesBurstsToFlushTimestamp() {
        List<Event> batched = simulator.batch(packets(10), NetworkModel.wifi(), 1L);
        assertEquals(10, batched.size());
        assertEquals(0L, batched.get(0).tick());
        long shared = batched.stream()
                .filter(event -> event.timestamp().equals(batched.get(2).timestamp()))
                .count();
        assertTrue(shared >= 3, "首批未聚合: " + shared);
        assertTrue(batched.get(batched.size() - 1).timestamp().isAfter(batched.get(0).timestamp()));
    }

    @Test
    void batchingIsDeterministicForSameSeed() {
        List<Event> packets = packets(20);
        assertEquals(simulator.batch(packets, NetworkModel.lte(), 8L),
                simulator.batch(packets, NetworkModel.lte(), 8L));
    }

    private static List<Event> packets(int count) {
        List<Event> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(new Event.Packet("legit-0", i, BASE.plusMillis(i * 50L), PLATFORM, "client->server",
                    "player_position", 32));
        }
        return out;
    }
}
