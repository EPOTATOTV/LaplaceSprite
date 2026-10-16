package io.laplace.sprite.cheats.inject;

import io.laplace.sprite.cheats.CheatInjectionRequest;
import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.CheatTier;
import io.laplace.sprite.core.model.CheatType;

import java.time.Instant;
import java.util.List;

/**
 * XRAY：直接锁定视线反方向的深层矿石坐标，正常玩家看不到这些方块。
 * 参数 {@code ore} 为矿石类型，{@code oresPerTick} 为每 tick 锁定数量，{@code range} 为探测距离。
 */
public final class XrayInjector extends AbstractCheatInjector {

    private static final int BURST_TICKS = 20;

    @Override
    public CheatType type() {
        return CheatType.XRAY;
    }

    @Override
    protected void apply(List<Event> baseline, InMemoryEventStream out, CheatInjectionRequest request,
                         CheatTier tier) {
        String ore = paramString(tier, "ore", "diamond_ore");
        int oresPerTick = Math.max(1, tier.intParam("oresPerTick", 2));
        int range = tier.intParam(primaryParamKey(), 48);
        emitAll(out, baseline);
        Anchor anchor = anchorOf(baseline);
        Instant origin = originOf(request.baseline());
        long start = lastTick(baseline) + 1;
        double radians = Math.toRadians(anchor.yaw());
        double behindX = Math.sin(radians);
        double behindZ = -Math.cos(radians);
        int baseX = (int) Math.floor(anchor.x());
        int baseY = (int) Math.floor(anchor.y());
        int baseZ = (int) Math.floor(anchor.z());
        for (int t = 0; t < BURST_TICKS; t++) {
            long tick = start + t;
            Instant timestamp = timestampAt(tick, origin);
            for (int i = 0; i < oresPerTick; i++) {
                int offset = range - i * 3;
                out.emit(new Event.Interact(request.playerId(), tick, timestamp, request.platform(), ore,
                        baseX + (int) Math.round(behindX * offset), baseY - 6 - i * 2,
                        baseZ + (int) Math.round(behindZ * offset), "mine"));
                out.emit(new Event.Packet(request.playerId(), tick, timestamp, request.platform(),
                        "SERVERBOUND", "PLAYER_DIGGING", 10));
            }
        }
    }
}
