package io.laplace.sprite.cheats.inject;

import io.laplace.sprite.cheats.CheatInjectionRequest;
import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.SampleTier;

import java.time.Instant;
import java.util.List;

/**
 * TECH：跨 tick 逻辑链与并发写异常。
 *
 * <p>方块实体链中断只丢掉中间一步，前后状态都保留，用来暴露「停在半完成状态」的问题；
 * 并发写把多个写入者的结果压在同一 tick，避免被 tick 顺序掩盖。</p>
 */
public final class TechAnomalyInjector extends AbstractSampleInjector {

    @Override
    public String id() {
        return "tech";
    }

    @Override
    protected void apply(List<Event> baseline, InMemoryEventStream out, CheatInjectionRequest request,
                         SampleTier tier) {
        emitAll(out, baseline);
        Instant origin = originOf(request.baseline());
        long tick = lastTick(baseline) + 1;
        String player = request.playerId();
        switch (request.sample().sampleType()) {
            case "BLOCK_ENTITY_CHAIN_BREAK" -> chainBreak(out, request, tier, tick, origin, player);
            case "BLOCK_ENTITY_CONCURRENT_WRITE" -> concurrentWrite(out, request, tier, tick, origin, player);
            case "SCHEDULER_REENTRY" -> schedulerReentry(out, request, tier, tick, origin, player);
            default -> out.emit(new Event.DataConsistency(player, tick, timestampAt(tick, origin),
                    request.platform(), "tech.sample", request.sample().sampleType(), "unhandled"));
        }
    }

    private static void chainBreak(InMemoryEventStream out, CheatInjectionRequest request, SampleTier tier,
                                   long tick, Instant origin, String player) {
        int steps = paramInt(tier, "chainSteps", 5);
        int x = paramInt(tier, "blockX", 12);
        int y = paramInt(tier, "blockY", 64);
        int z = paramInt(tier, "blockZ", 8);
        int skipped = steps / 2;
        for (int step = 0; step < steps; step++) {
            if (step == skipped) {
                continue;
            }
            long at = tick + step;
            String from = step == 0 ? "air" : "stage_" + (step - 1);
            out.emit(new Event.BlockChange(player, at, timestampAt(at, origin), request.platform(),
                    x, y, z, from, "stage_" + step));
        }
        out.emit(new Event.DataConsistency(player, tick + steps, timestampAt(tick + steps, origin),
                request.platform(), "block-entity:chain", "stage_" + (steps - 1), "stage_" + (skipped - 1)));
    }

    private static void concurrentWrite(InMemoryEventStream out, CheatInjectionRequest request, SampleTier tier,
                                        long tick, Instant origin, String player) {
        int writers = paramInt(tier, "writers", 3);
        int x = paramInt(tier, "blockX", 20);
        int y = paramInt(tier, "blockY", 64);
        int z = paramInt(tier, "blockZ", 4);
        for (int writer = 0; writer < writers; writer++) {
            String to = writer % 2 == 0 ? "redstone_lamp:lit" : "redstone_lamp:unlit";
            out.emit(new Event.BlockChange(player, tick, timestampAt(tick, origin), request.platform(),
                    x, y, z, "redstone_lamp:unlit", to));
        }
        out.emit(new Event.DataConsistency(player, tick, timestampAt(tick, origin), request.platform(),
                "block-entity:state", "lit/unlit x" + writers, "last-writer-wins"));
    }

    private static void schedulerReentry(InMemoryEventStream out, CheatInjectionRequest request, SampleTier tier,
                                         long tick, Instant origin, String player) {
        int reentries = paramInt(tier, "reentries", 2);
        double mspt = paramDouble(tier, "mspt", 80.0);
        for (int i = 0; i < reentries; i++) {
            out.emit(new Event.Tick(player, tick, timestampAt(tick, origin), request.platform(), 20.0, mspt));
        }
        out.emit(new Event.DataConsistency(player, tick, timestampAt(tick, origin), request.platform(),
                "scheduler:reentry", "count=" + reentries, "count=1"));
    }
}
