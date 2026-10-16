package io.laplace.sprite.cheats.inject;

import io.laplace.sprite.cheats.CheatInjectionRequest;
import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.CheatTier;
import io.laplace.sprite.core.model.CheatType;

import java.util.ArrayList;
import java.util.List;

/**
 * SPEED：按档位倍率放大逐 tick 的水平位移，超过原版步行/疾跑上限。
 * 参数 {@code speed} 为水平位移倍率，{@code sprint} 表示是否报疾跑状态。
 */
public final class SpeedInjector extends AbstractCheatInjector {

    @Override
    public CheatType type() {
        return CheatType.SPEED;
    }

    @Override
    protected void apply(List<Event> baseline, InMemoryEventStream out, CheatInjectionRequest request,
                         CheatTier tier) {
        double speed = tier.doubleParam(primaryParamKey(), 1.3);
        boolean sprint = tier.boolParam("sprint", true);
        List<Event.Move> moves = moveEvents(baseline);
        if (moves.isEmpty()) {
            emitAll(out, baseline);
            emitAll(out, scaledMoves(vanillaMoves(request, originOf(request.baseline()), 40), speed, sprint));
            return;
        }
        List<Event.Move> scaled = scaledMoves(moves, speed, sprint);
        int index = 0;
        for (Event event : baseline) {
            out.emit(event instanceof Event.Move ? scaled.get(index++) : event);
        }
    }

    private static List<Event.Move> scaledMoves(List<Event.Move> moves, double speed, boolean sprint) {
        List<Event.Move> result = new ArrayList<>(moves.size());
        Event.Move first = moves.get(0);
        result.add(new Event.Move(first.playerId(), first.tick(), first.timestamp(), first.platform(),
                first.x(), first.y(), first.z(), first.yaw(), first.pitch(), first.onGround(), sprint,
                first.sneaking()));
        double x = first.x();
        double z = first.z();
        for (int i = 1; i < moves.size(); i++) {
            Event.Move previous = moves.get(i - 1);
            Event.Move move = moves.get(i);
            x += (move.x() - previous.x()) * speed;
            z += (move.z() - previous.z()) * speed;
            result.add(new Event.Move(move.playerId(), move.tick(), move.timestamp(), move.platform(),
                    x, move.y(), z, move.yaw(), move.pitch(), move.onGround(), sprint, move.sneaking()));
        }
        return result;
    }
}
