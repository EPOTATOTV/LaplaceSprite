package io.laplace.sprite.cheats.inject;

import io.laplace.sprite.cheats.CheatInjectionRequest;
import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.CheatTier;
import io.laplace.sprite.core.model.CheatType;

import java.util.ArrayList;
import java.util.List;

/**
 * BLINK：积压若干 tick 后一次性跳变。窗口内服务端只见静止位置，释放 tick 上位置突跳。
 * 参数 {@code blinkTicks} 为积压时长，{@code distance} 为释放时的跳变距离。
 */
public final class BlinkInjector extends AbstractCheatInjector {

    @Override
    public CheatType type() {
        return CheatType.BLINK;
    }

    @Override
    protected void apply(List<Event> baseline, InMemoryEventStream out, CheatInjectionRequest request,
                         CheatTier tier) {
        int blinkTicks = Math.max(1, tier.intParam("blinkTicks", 8));
        double distance = tier.doubleParam(primaryParamKey(), 4.0);
        List<Event.Move> moves = moveEvents(baseline);
        if (moves.isEmpty()) {
            emitAll(out, baseline);
            emitAll(out, blinkMoves(vanillaMoves(request, originOf(request.baseline()), 40), blinkTicks, distance));
            return;
        }
        List<Event.Move> blinked = blinkMoves(moves, blinkTicks, distance);
        int index = 0;
        for (Event event : baseline) {
            out.emit(event instanceof Event.Move ? blinked.get(index++) : event);
        }
    }

    private static List<Event.Move> blinkMoves(List<Event.Move> moves, int blinkTicks, double distance) {
        List<Event.Move> result = new ArrayList<>(moves.size());
        int index = 0;
        while (index < moves.size()) {
            Event.Move start = moves.get(index);
            int releaseIndex = Math.min(moves.size() - 1, index + blinkTicks - 1);
            for (int i = index; i < releaseIndex; i++) {
                Event.Move held = moves.get(i);
                result.add(new Event.Move(held.playerId(), held.tick(), held.timestamp(), held.platform(),
                        start.x(), start.y(), start.z(), held.yaw(), held.pitch(), held.onGround(),
                        held.sprinting(), held.sneaking()));
            }
            Event.Move release = moves.get(releaseIndex);
            double radians = Math.toRadians(release.yaw());
            double jumpX = start.x() - Math.sin(radians) * distance;
            double jumpZ = start.z() + Math.cos(radians) * distance;
            result.add(new Event.Move(release.playerId(), release.tick(), release.timestamp(), release.platform(),
                    jumpX, release.y(), jumpZ, release.yaw(), release.pitch(), release.onGround(),
                    release.sprinting(), release.sneaking()));
            index = releaseIndex + 1;
        }
        return result;
    }
}
