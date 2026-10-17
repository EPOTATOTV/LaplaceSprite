package io.laplace.sprite.cheats.inject;

import io.laplace.sprite.cheats.CheatInjectionRequest;
import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.SampleTier;
import io.laplace.sprite.core.model.CheatType;

import java.time.Instant;
import java.util.List;

/**
 * TIMER：客户端计时倍率，单位服务端 tick 内发出更多移动/数据包事件，tick 间距被压缩。
 * 参数 {@code speedup} 为计时倍率（1.5 表示 1.5 倍速）。
 */
public final class TimerInjector extends AbstractCheatInjector {

    @Override
    public CheatType type() {
        return CheatType.TIMER;
    }

    @Override
    protected void apply(List<Event> baseline, InMemoryEventStream out, CheatInjectionRequest request,
                         SampleTier tier) {
        double speedup = tier.doubleParam(primaryParamKey(), 1.5);
        Instant origin = originOf(request.baseline());
        for (Event event : baseline) {
            out.emit(event);
            if (speedup <= 1.0 || !(event instanceof Event.Move || event instanceof Event.Packet)) {
                continue;
            }
            // 累计倍率：单位 tick 内的包数按 floor((tick+1)*speedup)-floor(tick*speedup) 展开
            int copies = (int) (Math.floor((event.tick() + 1) * speedup) - Math.floor(event.tick() * speedup));
            for (int i = 1; i < copies; i++) {
                long offset = Math.round(i * (TICK_MILLIS / (double) copies));
                out.emit(retime(event, timestampAt(event.tick(), origin).plusMillis(offset)));
            }
        }
    }

    private static Event retime(Event event, Instant timestamp) {
        if (event instanceof Event.Move move) {
            return new Event.Move(move.playerId(), move.tick(), timestamp, move.platform(), move.x(),
                    move.y(), move.z(), move.yaw(), move.pitch(), move.onGround(), move.sprinting(),
                    move.sneaking());
        }
        if (event instanceof Event.Packet packet) {
            return new Event.Packet(packet.playerId(), packet.tick(), timestamp, packet.platform(),
                    packet.direction(), packet.packetType(), packet.sizeBytes());
        }
        return event;
    }
}
