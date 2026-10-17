package io.laplace.sprite.cheats.inject;

import io.laplace.sprite.cheats.CheatInjectionRequest;
import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.SampleTier;

import java.time.Instant;
import java.util.List;

/**
 * PERF：单 tick 事件风暴，用来压被测目标的 tick 预算与队列上限。
 *
 * <p>风暴与降级后的 TPS 一起写入，避免只堆事件却看不出耗时代价。</p>
 */
public final class PerfAnomalyInjector extends AbstractSampleInjector {

    @Override
    public String id() {
        return "perf";
    }

    @Override
    protected void apply(List<Event> baseline, InMemoryEventStream out, CheatInjectionRequest request,
                         SampleTier tier) {
        emitAll(out, baseline);
        Instant origin = originOf(request.baseline());
        long tick = lastTick(baseline) + 1;
        String player = request.playerId();
        switch (request.sample().sampleType()) {
            case "ENTITY_SPIKE" -> entitySpike(out, request, tier, tick, origin, player);
            case "CHUNK_LOAD_STORM" -> chunkLoadStorm(out, request, tier, tick, origin, player);
            default -> out.emit(new Event.DataConsistency(player, tick, timestampAt(tick, origin),
                    request.platform(), "perf.sample", request.sample().sampleType(), "unhandled"));
        }
    }

    private static void entitySpike(InMemoryEventStream out, CheatInjectionRequest request, SampleTier tier,
                                    long tick, Instant origin, String player) {
        int entities = paramInt(tier, "entities", 240);
        double mspt = paramDouble(tier, "mspt", 120.0);
        for (int i = 0; i < entities; i++) {
            out.emit(new Event.Move(player, tick, timestampAt(tick, origin), request.platform(),
                    i * 0.05, 64.0, 0.5, 0f, 0f, true, false, false));
        }
        out.emit(new Event.Tick(player, tick, timestampAt(tick, origin), request.platform(), 20.0, mspt));
    }

    private static void chunkLoadStorm(InMemoryEventStream out, CheatInjectionRequest request, SampleTier tier,
                                       long tick, Instant origin, String player) {
        int chunks = paramInt(tier, "chunks", 256);
        int radius = paramInt(tier, "radius", 8);
        double tps = paramDouble(tier, "tps", 16.0);
        double mspt = paramDouble(tier, "mspt", 90.0);
        int width = Math.max(1, radius * 2 + 1);
        for (int i = 0; i < chunks; i++) {
            out.emit(new Event.ChunkLoad(player, tick, timestampAt(tick, origin), request.platform(),
                    i % width, i / width));
        }
        out.emit(new Event.Tick(player, tick, timestampAt(tick, origin), request.platform(), tps, mspt));
    }
}
