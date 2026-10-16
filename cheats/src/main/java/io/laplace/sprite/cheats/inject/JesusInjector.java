package io.laplace.sprite.cheats.inject;

import io.laplace.sprite.cheats.CheatInjectionRequest;
import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.CheatTier;
import io.laplace.sprite.core.model.CheatType;

import java.util.List;

/**
 * JESUS：在水面等流体表面行走，y 恒等于液面高度且上报 onGround。
 * 参数 {@code surfaceY} 为流体表面高度，{@code fluid} 为流体类型。
 */
public final class JesusInjector extends AbstractCheatInjector {

    @Override
    public CheatType type() {
        return CheatType.JESUS;
    }

    @Override
    protected void apply(List<Event> baseline, InMemoryEventStream out, CheatInjectionRequest request,
                         CheatTier tier) {
        double surfaceY = tier.doubleParam(primaryParamKey(), 62.5);
        String fluid = paramString(tier, "fluid", "water");
        List<Event.Move> moves = moveEvents(baseline);
        if (moves.isEmpty()) {
            emitAll(out, baseline);
            surface(out, request, vanillaMoves(request, originOf(request.baseline()), 40), surfaceY, fluid);
            return;
        }
        int index = 0;
        for (Event event : baseline) {
            if (event instanceof Event.Move) {
                surface(out, request, List.of(moves.get(index++)), surfaceY, fluid);
            } else {
                out.emit(event);
            }
        }
    }

    private static void surface(InMemoryEventStream out, CheatInjectionRequest request, List<Event.Move> moves,
                                double surfaceY, String fluid) {
        for (Event.Move move : moves) {
            out.emit(new Event.Move(move.playerId(), move.tick(), move.timestamp(), move.platform(),
                    move.x(), surfaceY, move.z(), move.yaw(), move.pitch(), true, move.sprinting(),
                    move.sneaking()));
            out.emit(new Event.BlockChange(request.playerId(), move.tick(), move.timestamp(), request.platform(),
                    (int) Math.floor(move.x()), (int) Math.floor(surfaceY), (int) Math.floor(move.z()),
                    "minecraft:" + fluid, "minecraft:" + fluid));
        }
    }
}
