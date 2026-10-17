package io.laplace.sprite.cheats.inject;

import io.laplace.sprite.cheats.CheatInjectionRequest;
import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.SampleTier;
import io.laplace.sprite.core.model.CheatType;

import java.time.Instant;
import java.util.List;

/**
 * AUTO_TOTEM：受击后立即换手不朽图腾，换手延迟远低于人类反应时间。
 * 参数 {@code swapDelayTicks} 为受击到完成换手的 tick 数。
 */
public final class AutoTotemInjector extends AbstractCheatInjector {

    private static final int CYCLES = 3;
    private static final long CYCLE_TICKS = 10L;

    @Override
    public CheatType type() {
        return CheatType.AUTO_TOTEM;
    }

    @Override
    protected void apply(List<Event> baseline, InMemoryEventStream out, CheatInjectionRequest request,
                         SampleTier tier) {
        int swapDelayTicks = Math.max(0, tier.intParam(primaryParamKey(), 1));
        emitAll(out, baseline);
        Instant origin = originOf(request.baseline());
        long start = lastTick(baseline) + 1;
        for (int cycle = 0; cycle < CYCLES; cycle++) {
            long hitTick = start + cycle * CYCLE_TICKS;
            long swapTick = hitTick + swapDelayTicks;
            out.emit(new Event.Combat(request.playerId(), hitTick, timestampAt(hitTick, origin),
                    request.platform(), "attacker-" + cycle, 3.0, 8, "hit"));
            out.emit(new Event.Inventory(request.playerId(), swapTick, timestampAt(swapTick, origin),
                    request.platform(), "offhand", "minecraft:air", 0));
            out.emit(new Event.Inventory(request.playerId(), swapTick, timestampAt(swapTick, origin),
                    request.platform(), "offhand", "minecraft:totem_of_undying", 1));
        }
    }
}
