package io.laplace.sprite.adapters;

import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;

import java.time.Instant;

/** 测试公共夹具：平台键与合法基线事件流。 */
final class AdapterTestSupport {

    static final Instant ORIGIN = Instant.parse("2026-01-01T00:00:00Z");
    static final String PLAYER = "SimID-legit";

    private AdapterTestSupport() {
    }

    /** 取一个落在该适配器支持区间内的矩阵键。 */
    static PlatformKey keyFor(AbstractLaplaceAdapter adapter) {
        return switch (adapter.family()) {
            case WATERFALL -> PlatformKey.of(PlatformFamily.WATERFALL, "1.20.1");
            case NUKKIT -> PlatformKey.of(PlatformFamily.NUKKIT, "1.20.4");
            case POWER_NUKKIT_X -> PlatformKey.of(PlatformFamily.POWER_NUKKIT_X, "1.21.0");
            case FORGE -> new PlatformKey(PlatformFamily.FORGE, "1.21.11", "61.2.3");
            case NEOFORGE -> new PlatformKey(PlatformFamily.NEOFORGE, "1.21.11", "21.11.0");
            case FABRIC -> new PlatformKey(PlatformFamily.FABRIC, "1.21.11", "0.19.5");
            case QUILT -> new PlatformKey(PlatformFamily.QUILT, "1.21.11", "0.30.0");
            default -> PlatformKey.of(adapter.family(), "1.21.11");
        };
    }

    /**
     * 合法基线：40 tick 步行，水平位移 0.215 格/tick（模拟器步行模型口径），落地、疾跑为真。
     *
     * <p>启发式引擎在这条流上必须一个 flag 都不出。</p>
     */
    static InMemoryEventStream legitBaseline(PlatformKey platform) {
        InMemoryEventStream stream = new InMemoryEventStream();
        double x = 0.5;
        for (int tick = 0; tick < 40; tick++) {
            Instant at = ORIGIN.plusMillis(tick * 50L);
            stream.emit(new Event.Move(PLAYER, tick, at, platform, x, 64.0, 0.5, 0f, 0f, true, true, false));
            if (tick % 5 == 0) {
                stream.emit(new Event.Packet(PLAYER, tick, at, platform, "SERVERBOUND", "MOVE", 32));
            }
            stream.emit(new Event.Tick(PLAYER, tick, at, platform, 20.0, 3.5));
            x += 0.215;
        }
        return stream;
    }

    /** 构造一条 Move 序列，delta 由参数决定。 */
    static InMemoryEventStream moveStream(PlatformKey platform, int ticks, double delta, boolean onGround,
                                          double verticalDelta, boolean sprinting) {
        InMemoryEventStream stream = new InMemoryEventStream();
        double x = 0.5;
        double y = 64.0;
        for (int tick = 0; tick < ticks; tick++) {
            stream.emit(new Event.Move(PLAYER, tick, ORIGIN.plusMillis(tick * 50L), platform, x, y, 0.5,
                    0f, 0f, onGround, sprinting, false));
            x += delta;
            y += verticalDelta;
        }
        return stream;
    }
}
