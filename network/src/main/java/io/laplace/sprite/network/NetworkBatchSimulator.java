package io.laplace.sprite.network;

import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.event.EventType;
import io.laplace.sprite.core.model.NetworkModel;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 包批处理模拟（4.3）：把连续上行包按 wifi/lte 的窗口聚成一批，整批在同一时刻到达。
 *
 * <p>批大小按 seed 在窗口范围内浮动，非包事件原样保留，只做顺序上的重排。</p>
 */
public final class NetworkBatchSimulator {

    public List<Event> batch(List<Event> packets, NetworkModel model, long seed) {
        Random rng = new Random(seed);
        int window = switch (model.batchPattern()) {
            case NONE -> 1;
            case WIFI -> 3 + rng.nextInt(3);
            case LTE -> 6 + rng.nextInt(5);
        };
        if (window <= 1) {
            return List.copyOf(packets);
        }
        List<Event> out = new ArrayList<>(packets.size());
        for (int start = 0; start < packets.size(); start += window) {
            List<Event> burst = packets.subList(start, Math.min(start + window, packets.size()));
            Instant flush = burst.get(burst.size() - 1).timestamp();
            for (Event event : burst) {
                out.add(event.type() == EventType.PACKET ? flushAt(event, flush) : event);
            }
        }
        return out;
    }

    private static Event flushAt(Event event, Instant flush) {
        Event.Packet packet = (Event.Packet) event;
        return new Event.Packet(packet.playerId(), packet.tick(), flush, packet.platform(),
                packet.direction(), packet.packetType(), packet.sizeBytes());
    }
}
