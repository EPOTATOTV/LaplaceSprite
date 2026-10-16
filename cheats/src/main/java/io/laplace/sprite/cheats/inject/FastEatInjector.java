package io.laplace.sprite.cheats.inject;

import io.laplace.sprite.cheats.CheatInjectionRequest;
import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.CheatTier;
import io.laplace.sprite.core.model.CheatType;

import java.time.Instant;
import java.util.List;

/**
 * FAST_EAT：在远少于原版的 tick 内完成进食（原版食物约 32 tick）。
 * 参数 {@code consumeTicks} 为完成进食的 tick 数，{@code item} 为食物类型。
 */
public final class FastEatInjector extends AbstractCheatInjector {

    @Override
    public CheatType type() {
        return CheatType.FAST_EAT;
    }

    @Override
    protected void apply(List<Event> baseline, InMemoryEventStream out, CheatInjectionRequest request,
                         CheatTier tier) {
        int consumeTicks = Math.max(1, tier.intParam(primaryParamKey(), 4));
        String item = paramString(tier, "item", "golden_apple");
        emitAll(out, baseline);
        Anchor anchor = anchorOf(baseline);
        Instant origin = originOf(request.baseline());
        long useTick = lastTick(baseline) + 1;
        long doneTick = useTick + consumeTicks;
        out.emit(new Event.Interact(request.playerId(), useTick, timestampAt(useTick, origin),
                request.platform(), item, (int) Math.floor(anchor.x()), (int) Math.floor(anchor.y()),
                (int) Math.floor(anchor.z()), "use"));
        out.emit(new Event.Packet(request.playerId(), useTick, timestampAt(useTick, origin),
                request.platform(), "SERVERBOUND", "USE_ITEM", 9));
        out.emit(new Event.Inventory(request.playerId(), doneTick, timestampAt(doneTick, origin),
                request.platform(), "mainhand", "minecraft:" + item, 0));
        out.emit(new Event.Packet(request.playerId(), doneTick, timestampAt(doneTick, origin),
                request.platform(), "SERVERBOUND", "HELD_ITEM_CHANGE", 2));
    }
}
