package io.laplace.sprite.cheats.inject;

import io.laplace.sprite.cheats.CheatInjectionRequest;
import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.SampleTier;
import io.laplace.sprite.core.model.CheatType;

import java.util.List;

/**
 * SCAFFOLD：移动时每 tick 自动在脚下补方块。
 * 参数 {@code block} 为放置方块类型，{@code blocksPerTick} 为每 tick 放置数量。
 */
public final class ScaffoldInjector extends AbstractCheatInjector {

    @Override
    public CheatType type() {
        return CheatType.SCAFFOLD;
    }

    @Override
    protected void apply(List<Event> baseline, InMemoryEventStream out, CheatInjectionRequest request,
                         SampleTier tier) {
        String block = paramString(tier, "block", "obsidian");
        int perTick = Math.max(1, tier.intParam(primaryParamKey(), 1));
        if (moveEvents(baseline).isEmpty()) {
            emitAll(out, baseline);
            for (Event.Move move : vanillaMoves(request, originOf(request.baseline()), 20)) {
                place(out, request, move, block, perTick);
            }
            return;
        }
        for (Event event : baseline) {
            out.emit(event);
            if (event instanceof Event.Move move) {
                place(out, request, move, block, perTick);
            }
        }
    }

    private static void place(InMemoryEventStream out, CheatInjectionRequest request, Event.Move move,
                              String block, int perTick) {
        int blockX = (int) Math.floor(move.x());
        int blockZ = (int) Math.floor(move.z());
        int baseY = (int) Math.floor(move.y()) - 1;
        for (int i = 0; i < perTick; i++) {
            out.emit(new Event.Interact(request.playerId(), move.tick(), move.timestamp(), request.platform(),
                    block, blockX, baseY - i, blockZ, "place"));
            out.emit(new Event.BlockChange(request.playerId(), move.tick(), move.timestamp(), request.platform(),
                    blockX, baseY - i, blockZ, "minecraft:air", "minecraft:" + block));
        }
    }
}
