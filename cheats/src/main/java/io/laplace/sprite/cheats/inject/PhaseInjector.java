package io.laplace.sprite.cheats.inject;

import io.laplace.sprite.cheats.CheatInjectionRequest;
import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.CheatTier;
import io.laplace.sprite.core.model.CheatType;

import java.time.Instant;
import java.util.List;

/**
 * PHASE：位置穿入实体方块内部。先把所处坐标报成实体方块，再把玩家坐标写进该方块。
 * 参数 {@code block} 为穿入的方块类型，{@code depth} 为穿入层数。
 */
public final class PhaseInjector extends AbstractCheatInjector {

    @Override
    public CheatType type() {
        return CheatType.PHASE;
    }

    @Override
    protected void apply(List<Event> baseline, InMemoryEventStream out, CheatInjectionRequest request,
                         CheatTier tier) {
        String block = paramString(tier, "block", "stone");
        int depth = Math.max(1, tier.intParam(primaryParamKey(), 2));
        emitAll(out, baseline);
        Anchor anchor = anchorOf(baseline);
        Instant origin = originOf(request.baseline());
        long start = lastTick(baseline) + 1;
        int blockX = (int) Math.floor(anchor.x());
        int blockZ = (int) Math.floor(anchor.z());
        int surfaceY = (int) Math.floor(anchor.y());
        for (int i = 0; i < depth; i++) {
            long tick = start + i;
            Instant timestamp = timestampAt(tick, origin);
            int blockY = surfaceY - i;
            out.emit(new Event.BlockChange(request.playerId(), tick, timestamp, request.platform(),
                    blockX, blockY, blockZ, "minecraft:air", "minecraft:" + block));
            out.emit(new Event.Move(request.playerId(), tick, timestamp, request.platform(),
                    blockX + 0.5, blockY, blockZ + 0.5, anchor.yaw(), anchor.pitch(), false, false, false));
        }
    }
}
