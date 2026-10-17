package io.laplace.sprite.cheats.inject;

import io.laplace.sprite.cheats.CheatInjectionRequest;
import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.SampleTier;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * NETWORK：丢包、乱序与超时断连。
 *
 * <p>只改动上行 Packet 的顺序与存活，不改写移动/战斗等业务事件；这样能把网络层异常与
 * 业务层异常分开观察。</p>
 */
public final class NetworkAnomalyInjector extends AbstractSampleInjector {

    @Override
    public String id() {
        return "network";
    }

    @Override
    protected void apply(List<Event> baseline, InMemoryEventStream out, CheatInjectionRequest request,
                         SampleTier tier) {
        List<Event> events = new ArrayList<>(baseline);
        Instant origin = originOf(request.baseline());
        long last = lastTick(baseline);
        switch (request.sample().sampleType()) {
            case "PACKET_LOSS_BURST" -> dropPackets(events, tier);
            case "REORDER_BURST" -> reorderPackets(events, tier);
            case "TIMEOUT_DISCONNECT" -> timeout(events, request, tier, last, origin);
            default -> events.add(new Event.DataConsistency(request.playerId(), last + 1,
                    timestampAt(last + 1, origin), request.platform(), "network.sample",
                    request.sample().sampleType(), "unhandled"));
        }
        emitAll(out, events);
    }

    private static void dropPackets(List<Event> events, SampleTier tier) {
        int dropEvery = Math.max(1, paramInt(tier, "dropEvery", 4));
        int start = paramInt(tier, "startTick", 0);
        int window = paramInt(tier, "burstTicks", 40);
        List<Event> kept = new ArrayList<>(events.size());
        int packetIndex = 0;
        for (Event event : events) {
            boolean inWindow = event.tick() >= start && event.tick() < start + window;
            if (event instanceof Event.Packet && inWindow && packetIndex++ % dropEvery == 0) {
                continue;
            }
            kept.add(event);
        }
        events.clear();
        events.addAll(kept);
    }

    private static void reorderPackets(List<Event> events, SampleTier tier) {
        int window = Math.max(1, paramInt(tier, "windowTicks", 40));
        List<Integer> positions = new ArrayList<>();
        List<Event> packets = new ArrayList<>();
        for (int i = 0; i < events.size(); i++) {
            Event event = events.get(i);
            if (event instanceof Event.Packet && event.tick() < window) {
                positions.add(i);
                packets.add(event);
            }
        }
        for (int i = 0; i < positions.size(); i++) {
            events.set(positions.get(i), packets.get(packets.size() - 1 - i));
        }
    }

    private static void timeout(List<Event> events, CheatInjectionRequest request, SampleTier tier,
                                long lastTick, Instant origin) {
        int timeoutTick = paramInt(tier, "timeoutTick", 20);
        String reason = paramString(tier, "reason", "read_timeout");
        List<Event> kept = new ArrayList<>(events.size());
        for (Event event : events) {
            if (!(event instanceof Event.Packet) || event.tick() < timeoutTick) {
                kept.add(event);
            }
        }
        long tick = lastTick + 1;
        kept.add(new Event.Disconnect(request.playerId(), tick, timestampAt(tick, origin),
                request.platform(), reason));
        events.clear();
        events.addAll(kept);
    }
}
