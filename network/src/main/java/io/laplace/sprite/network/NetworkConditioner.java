package io.laplace.sprite.network;

import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.event.EventType;
import io.laplace.sprite.core.model.NetworkModel;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 网络条件器（4.3）：按 {@link NetworkModel} 对事件流做确定性延迟、丢包与重排模拟。
 *
 * <p>丢包与重排只作用于包事件（{@code PACKET}），位移等业务事件只被延迟，序列内部顺序保持不变。
 * 事件记录不可变，延迟统一体现在 {@code timestamp()} 上，tick 与平台标签原样保留。</p>
 */
public final class NetworkConditioner {

    private static final double SPIKE_PROBABILITY = 0.03;

    public int sampleLatencyMs(NetworkModel model, Random rng) {
        int span = model.latencyMaxMs() - model.latencyMinMs();
        int latency = model.latencyMinMs() + (span == 0 ? 0 : rng.nextInt(span + 1));
        if (model.jitterMs() > 0) {
            latency += rng.nextInt(model.jitterMs() + 1);
        }
        if (model.spikeMs() > 0 && rng.nextDouble() < SPIKE_PROBABILITY) {
            latency += model.spikeMs();
        }
        return Math.max(0, latency);
    }

    public boolean drop(NetworkModel model, Random rng) {
        return model.packetLossRate() > 0 && rng.nextDouble() < model.packetLossRate();
    }

    public boolean reorder(NetworkModel model, Random rng) {
        return model.reorderRate() > 0 && rng.nextDouble() < model.reorderRate();
    }

    public EventStream condition(NetworkModel model, EventStream in, long seed) {
        Random rng = new Random(seed ^ model.seed());
        List<Event> conditioned = new ArrayList<>();
        for (Event event : in.events()) {
            if (event.type() == EventType.PACKET && drop(model, rng)) {
                continue;
            }
            conditioned.add(shift(event, sampleLatencyMs(model, rng)));
        }
        if (model.reorderRate() > 0) {
            conditioned = applyReorder(model, conditioned, rng);
        }
        return new InMemoryEventStream(conditioned);
    }

    /** 批处理说明文案，用于报告里解释 wifi/lte 的聚合行为。 */
    public List<String> batchingNotes(NetworkModel model) {
        List<String> notes = new ArrayList<>();
        notes.add("延迟段 " + model.latencyMinMs() + "-" + model.latencyMaxMs() + "ms，抖动 "
                + model.jitterMs() + "ms，尖峰 " + model.spikeMs() + "ms");
        switch (model.batchPattern()) {
            case WIFI -> notes.add("wifi 批处理窗口约 50ms，上行位置包按 4 个一批下发");
            case LTE -> notes.add("lte 批处理窗口约 120ms，上行位置包按 8 个一批下发，丢包率 "
                    + model.packetLossRate());
            case NONE -> notes.add("无批处理，逐包发送");
        }
        return notes;
    }

    /** 把包事件与其后一个事件对调，模拟乱序到达。 */
    private List<Event> applyReorder(NetworkModel model, List<Event> events, Random rng) {
        List<Event> out = new ArrayList<>(events);
        for (int i = 0; i + 1 < out.size(); i++) {
            if (out.get(i + 1).type() == EventType.PACKET && reorder(model, rng)) {
                Event current = out.get(i);
                out.set(i, out.get(i + 1));
                out.set(i + 1, current);
            }
        }
        return out;
    }

    private static Event shift(Event event, long latencyMs) {
        Instant timestamp = event.timestamp().plusMillis(latencyMs);
        return switch (event) {
            case Event.PlayerJoin e -> new Event.PlayerJoin(e.playerId(), e.tick(), timestamp, e.platform(),
                    e.world(), e.x(), e.y(), e.z());
            case Event.PlayerLeave e -> new Event.PlayerLeave(e.playerId(), e.tick(), timestamp, e.platform(),
                    e.reason());
            case Event.ProxyPlayerJoin e -> new Event.ProxyPlayerJoin(e.playerId(), e.tick(), timestamp,
                    e.platform(), e.targetServer());
            case Event.ProxyPlayerLeave e -> new Event.ProxyPlayerLeave(e.playerId(), e.tick(), timestamp,
                    e.platform(), e.targetServer(), e.reason());
            case Event.ProxyChat e -> new Event.ProxyChat(e.playerId(), e.tick(), timestamp, e.platform(),
                    e.message());
            case Event.Disconnect e -> new Event.Disconnect(e.playerId(), e.tick(), timestamp, e.platform(),
                    e.reason());
            case Event.Move e -> new Event.Move(e.playerId(), e.tick(), timestamp, e.platform(), e.x(), e.y(),
                    e.z(), e.yaw(), e.pitch(), e.onGround(), e.sprinting(), e.sneaking());
            case Event.Combat e -> new Event.Combat(e.playerId(), e.tick(), timestamp, e.platform(),
                    e.targetId(), e.reach(), e.clicksPerSecond(), e.swingType());
            case Event.Interact e -> new Event.Interact(e.playerId(), e.tick(), timestamp, e.platform(),
                    e.blockType(), e.blockX(), e.blockY(), e.blockZ(), e.action());
            case Event.Chat e -> new Event.Chat(e.playerId(), e.tick(), timestamp, e.platform(), e.message());
            case Event.Inventory e -> new Event.Inventory(e.playerId(), e.tick(), timestamp, e.platform(),
                    e.slot(), e.itemId(), e.amount());
            case Event.Packet e -> new Event.Packet(e.playerId(), e.tick(), timestamp, e.platform(),
                    e.direction(), e.packetType(), e.sizeBytes());
            case Event.ChunkLoad e -> new Event.ChunkLoad(e.playerId(), e.tick(), timestamp, e.platform(),
                    e.chunkX(), e.chunkZ());
            case Event.BlockChange e -> new Event.BlockChange(e.playerId(), e.tick(), timestamp, e.platform(),
                    e.blockX(), e.blockY(), e.blockZ(), e.from(), e.to());
            case Event.DataConsistency e -> new Event.DataConsistency(e.playerId(), e.tick(), timestamp,
                    e.platform(), e.key(), e.clientValue(), e.serverValue());
            case Event.Brand e -> new Event.Brand(e.playerId(), e.tick(), timestamp, e.platform(), e.brand());
            case Event.ModHash e -> new Event.ModHash(e.playerId(), e.tick(), timestamp, e.platform(),
                    e.modId(), e.sha256());
            case Event.Addon e -> new Event.Addon(e.playerId(), e.tick(), timestamp, e.platform(), e.addonId(),
                    e.version());
            case Event.RegionTick e -> new Event.RegionTick(e.playerId(), e.tick(), timestamp, e.platform(),
                    e.regionId(), e.regionContext());
            case Event.Tick e -> new Event.Tick(e.playerId(), e.tick(), timestamp, e.platform(), e.tps(),
                    e.mspt());
            case Event.Registry e -> new Event.Registry(e.playerId(), e.tick(), timestamp, e.platform(),
                    e.registry(), e.entry(), e.phase());
            case Event.GameTestSuite e -> new Event.GameTestSuite(e.playerId(), e.tick(), timestamp,
                    e.platform(), e.suite(), e.testName(), e.passed(), e.failure());
        };
    }
}
