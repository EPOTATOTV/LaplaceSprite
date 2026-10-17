package io.laplace.sprite.cheats.inject;

import io.laplace.sprite.cheats.CheatInjectionRequest;
import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.SampleTier;
import io.laplace.sprite.core.model.CheatType;

import java.util.ArrayList;
import java.util.List;

/**
 * FLY：持续上升且 onGround 恒为 false，缺少下落段。
 * 参数 {@code verticalSpeed} 为逐 tick 上升高度（原版跳跃峰值约 0.42）。
 */
public final class FlyInjector extends AbstractCheatInjector {

    @Override
    public CheatType type() {
        return CheatType.FLY;
    }

    @Override
    protected void apply(List<Event> baseline, InMemoryEventStream out, CheatInjectionRequest request,
                         SampleTier tier) {
        double verticalSpeed = tier.doubleParam(primaryParamKey(), 0.42);
        List<Event.Move> moves = moveEvents(baseline);
        if (moves.isEmpty()) {
            emitAll(out, baseline);
            emitAll(out, liftedMoves(vanillaMoves(request, originOf(request.baseline()), 40), verticalSpeed));
            return;
        }
        List<Event.Move> lifted = liftedMoves(moves, verticalSpeed);
        int index = 0;
        for (Event event : baseline) {
            out.emit(event instanceof Event.Move ? lifted.get(index++) : event);
        }
    }

    private static List<Event.Move> liftedMoves(List<Event.Move> moves, double verticalSpeed) {
        List<Event.Move> result = new ArrayList<>(moves.size());
        double y = moves.get(0).y();
        for (int i = 0; i < moves.size(); i++) {
            Event.Move move = moves.get(i);
            if (i > 0) {
                y += verticalSpeed;
            }
            result.add(new Event.Move(move.playerId(), move.tick(), move.timestamp(), move.platform(),
                    move.x(), y, move.z(), move.yaw(), move.pitch(), false, move.sprinting(), move.sneaking()));
        }
        return result;
    }
}
