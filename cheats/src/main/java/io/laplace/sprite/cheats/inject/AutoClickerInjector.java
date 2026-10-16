package io.laplace.sprite.cheats.inject;

import io.laplace.sprite.cheats.CheatInjectionRequest;
import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.CheatTier;
import io.laplace.sprite.core.model.CheatType;

import java.time.Instant;
import java.util.List;

/**
 * AUTO_CLICKER：逐 tick 恒定点击，CPS 落在 12–25 且无人类抖动（方差为 0）。
 */
public final class AutoClickerInjector extends AbstractCheatInjector {

    private static final int BURST_TICKS = 40;

    @Override
    public CheatType type() {
        return CheatType.AUTO_CLICKER;
    }

    @Override
    protected void apply(List<Event> baseline, InMemoryEventStream out, CheatInjectionRequest request,
                         CheatTier tier) {
        int cps = tier.intParam(primaryParamKey(), 16);
        emitAll(out, baseline);
        Instant origin = originOf(request.baseline());
        long start = lastTick(baseline) + 1;
        for (int i = 0; i < BURST_TICKS; i++) {
            long tick = start + i;
            out.emit(new Event.Combat(request.playerId(), tick, timestampAt(tick, origin), request.platform(),
                    "target-1", 3.0, cps, "auto"));
        }
    }
}
