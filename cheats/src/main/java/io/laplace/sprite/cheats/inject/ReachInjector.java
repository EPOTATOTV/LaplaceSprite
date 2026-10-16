package io.laplace.sprite.cheats.inject;

import io.laplace.sprite.cheats.CheatInjectionRequest;
import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.CheatTier;
import io.laplace.sprite.core.model.CheatType;

import java.time.Instant;
import java.util.List;

/**
 * REACH：把战斗事件的攻击距离推到原版上限（3.0）之上。
 * 基线已有战斗中继时改写最大距离，否则按档位距离补一段连击。
 */
public final class ReachInjector extends AbstractCheatInjector {

    private static final int BURST_TICKS = 20;

    @Override
    public CheatType type() {
        return CheatType.REACH;
    }

    @Override
    protected void apply(List<Event> baseline, InMemoryEventStream out, CheatInjectionRequest request,
                         CheatTier tier) {
        double reach = tier.doubleParam(primaryParamKey(), 4.5);
        int cps = tier.intParam("clicksPerSecond", 12);
        boolean hasCombat = baseline.stream().anyMatch(Event.Combat.class::isInstance);
        if (hasCombat) {
            for (Event event : baseline) {
                if (event instanceof Event.Combat combat) {
                    out.emit(new Event.Combat(combat.playerId(), combat.tick(), combat.timestamp(),
                            combat.platform(), combat.targetId(), Math.max(reach, combat.reach()),
                            Math.max(cps, combat.clicksPerSecond()), combat.swingType()));
                } else {
                    out.emit(event);
                }
            }
            return;
        }
        emitAll(out, baseline);
        Instant origin = originOf(request.baseline());
        long start = lastTick(baseline) + 1;
        for (int i = 0; i < BURST_TICKS; i++) {
            long tick = start + i;
            out.emit(new Event.Combat(request.playerId(), tick, timestampAt(tick, origin), request.platform(),
                    "target-1", reach, cps, "reach"));
        }
    }
}
