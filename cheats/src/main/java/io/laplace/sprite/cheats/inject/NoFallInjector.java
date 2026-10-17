package io.laplace.sprite.cheats.inject;

import io.laplace.sprite.cheats.CheatInjectionRequest;
import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.SampleTier;
import io.laplace.sprite.core.model.CheatType;

import java.util.ArrayList;
import java.util.List;

/**
 * NO_FALL：高速下落却始终上报 onGround，缺少落地减速段。
 * 参数 {@code fallSpeed} 为逐 tick 下落高度，{@code spoofGround} 表示是否伪造落地标记。
 */
public final class NoFallInjector extends AbstractCheatInjector {

    @Override
    public CheatType type() {
        return CheatType.NO_FALL;
    }

    @Override
    protected void apply(List<Event> baseline, InMemoryEventStream out, CheatInjectionRequest request,
                         SampleTier tier) {
        double fallSpeed = tier.doubleParam(primaryParamKey(), 0.6);
        boolean spoofGround = tier.boolParam("spoofGround", true);
        List<Event.Move> moves = moveEvents(baseline);
        List<Event.Move> fallen = fallenMoves(
                moves.isEmpty() ? vanillaMoves(request, originOf(request.baseline()), 40) : moves,
                fallSpeed, spoofGround);
        if (moves.isEmpty()) {
            emitAll(out, baseline);
            for (Event.Move move : fallen) {
                out.emit(move);
                out.emit(groundPacket(request, move));
            }
            return;
        }
        int index = 0;
        for (Event event : baseline) {
            if (event instanceof Event.Move) {
                Event.Move move = fallen.get(index++);
                out.emit(move);
                out.emit(groundPacket(request, move));
            } else {
                out.emit(event);
            }
        }
    }

    private static List<Event.Move> fallenMoves(List<Event.Move> moves, double fallSpeed, boolean spoofGround) {
        List<Event.Move> result = new ArrayList<>(moves.size());
        double y = moves.get(0).y();
        for (int i = 0; i < moves.size(); i++) {
            Event.Move move = moves.get(i);
            if (i > 0) {
                y -= fallSpeed;
            }
            result.add(new Event.Move(move.playerId(), move.tick(), move.timestamp(), move.platform(),
                    move.x(), y, move.z(), move.yaw(), move.pitch(), spoofGround, move.sprinting(),
                    move.sneaking()));
        }
        return result;
    }

    private static Event.Packet groundPacket(CheatInjectionRequest request, Event.Move move) {
        return new Event.Packet(request.playerId(), move.tick(), move.timestamp(), request.platform(),
                "SERVERBOUND", "PLAYER_GROUND", 1);
    }
}
