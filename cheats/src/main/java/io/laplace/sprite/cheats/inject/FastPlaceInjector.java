package io.laplace.sprite.cheats.inject;

import io.laplace.sprite.cheats.CheatInjectionRequest;
import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.SampleTier;
import io.laplace.sprite.core.model.CheatType;

import java.time.Instant;
import java.util.List;

/**
 * FAST_PLACE：放置速率远超人类点击上限（原版约每 4 tick 一次）。
 * 参数 {@code placementsPerTick} 为每 tick 放置次数，{@code block} 为方块类型。
 */
public final class FastPlaceInjector extends AbstractCheatInjector {

    private static final int BURST_TICKS = 20;

    @Override
    public CheatType type() {
        return CheatType.FAST_PLACE;
    }

    @Override
    protected void apply(List<Event> baseline, InMemoryEventStream out, CheatInjectionRequest request,
                         SampleTier tier) {
        int perTick = Math.max(1, tier.intParam(primaryParamKey(), 6));
        String block = paramString(tier, "block", "netherrack");
        emitAll(out, baseline);
        Anchor anchor = anchorOf(baseline);
        Instant origin = originOf(request.baseline());
        long start = lastTick(baseline) + 1;
        int blockX = (int) Math.floor(anchor.x());
        int blockY = (int) Math.floor(anchor.y()) - 2;
        int blockZ = (int) Math.floor(anchor.z());
        for (int t = 0; t < BURST_TICKS; t++) {
            long tick = start + t;
            Instant timestamp = timestampAt(tick, origin);
            for (int i = 0; i < perTick; i++) {
                out.emit(new Event.Interact(request.playerId(), tick, timestamp, request.platform(),
                        block, blockX + i, blockY, blockZ, "place"));
            }
            out.emit(new Event.BlockChange(request.playerId(), tick, timestamp, request.platform(),
                    blockX, blockY, blockZ, "minecraft:air", "minecraft:" + block));
        }
    }
}
