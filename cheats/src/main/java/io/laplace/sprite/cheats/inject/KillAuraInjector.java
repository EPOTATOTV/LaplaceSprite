package io.laplace.sprite.cheats.inject;

import io.laplace.sprite.cheats.CheatInjectionRequest;
import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.SampleTier;
import io.laplace.sprite.core.model.CheatType;

import java.time.Instant;
import java.util.List;

/**
 * KILL_AURA：同一 tick 同时命中多个目标，距离超出原版且命中率 100%。
 */
public final class KillAuraInjector extends AbstractCheatInjector {

    private static final int BURST_TICKS = 20;
    private static final int AURA_CPS = 20;

    @Override
    public CheatType type() {
        return CheatType.KILL_AURA;
    }

    @Override
    protected void apply(List<Event> baseline, InMemoryEventStream out, CheatInjectionRequest request,
                         SampleTier tier) {
        double reach = tier.doubleParam("reach", 4.5);
        int targets = Math.max(1, tier.intParam(primaryParamKey(), 3));
        emitAll(out, baseline);
        Instant origin = originOf(request.baseline());
        long start = lastTick(baseline) + 1;
        for (int i = 0; i < BURST_TICKS; i++) {
            long tick = start + i;
            Instant timestamp = timestampAt(tick, origin);
            for (int target = 0; target < targets; target++) {
                out.emit(new Event.Combat(request.playerId(), tick, timestamp, request.platform(),
                        "aura-target-" + target, reach, AURA_CPS, "aura"));
            }
        }
    }
}
