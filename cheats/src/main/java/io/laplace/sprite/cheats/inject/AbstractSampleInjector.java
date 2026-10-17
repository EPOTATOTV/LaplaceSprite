package io.laplace.sprite.cheats.inject;

import io.laplace.sprite.cheats.CheatInjectionRequest;
import io.laplace.sprite.cheats.SampleInjector;
import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.model.ExpectedDetection;
import io.laplace.sprite.core.model.SampleTier;
import io.laplace.sprite.core.model.SampleTierName;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 注入器公共骨架：复制合法基线、解析档位参数。
 * 具体行为由子类在 {@link #apply} 中实现，基线流本身只读。
 */
public abstract class AbstractSampleInjector implements SampleInjector {

    /** 服务端一 tick 的基准时长（20TPS）。 */
    protected static final long TICK_MILLIS = 50L;

    @Override
    public EventStream inject(CheatInjectionRequest request) {
        InMemoryEventStream out = new InMemoryEventStream();
        apply(baselineEvents(request), out, request, resolveTier(request));
        return out;
    }

    /** 基于合法基线写出样本事件流。 */
    protected abstract void apply(List<Event> baseline, InMemoryEventStream out,
                                  CheatInjectionRequest request, SampleTier tier);

    protected static List<Event> baselineEvents(CheatInjectionRequest request) {
        EventStream baseline = request.baseline();
        return baseline == null ? List.of() : baseline.events();
    }

    protected static SampleTier resolveTier(CheatInjectionRequest request) {
        return request.spec().tier(request.tier())
                .or(() -> request.spec().tier(SampleTierName.DEFAULT))
                .orElseGet(() -> new SampleTier(request.tier(), Map.of(), ExpectedDetection.detected()));
    }

    protected static List<Event.Move> moveEvents(List<Event> events) {
        return events.stream()
                .filter(Event.Move.class::isInstance)
                .map(Event.Move.class::cast)
                .toList();
    }

    protected static void emitAll(InMemoryEventStream out, List<? extends Event> events) {
        out.emitAll(List.copyOf(events));
    }

    protected static long lastTick(List<Event> events) {
        return events.stream().mapToLong(Event::tick).max().orElse(0L);
    }

    protected static Instant originOf(EventStream baseline) {
        if (baseline == null || baseline.events().isEmpty()) {
            return Instant.EPOCH;
        }
        return baseline.events().get(0).timestamp();
    }

    protected static Instant timestampAt(long tick, Instant origin) {
        return origin.plusMillis(tick * TICK_MILLIS);
    }

    protected static String paramString(SampleTier tier, String key, String fallback) {
        Object value = tier.params().get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    protected static double paramDouble(SampleTier tier, String key, double fallback) {
        return tier.doubleParam(key, fallback);
    }

    protected static int paramInt(SampleTier tier, String key, int fallback) {
        return tier.intParam(key, fallback);
    }

    /** 玩家锚点：取基线中最后一次 Move，缺省落在世界原点上方。 */
    protected static Anchor anchorOf(List<Event> events) {
        for (int i = events.size() - 1; i >= 0; i--) {
            if (events.get(i) instanceof Event.Move move) {
                return new Anchor(move.x(), move.y(), move.z(), move.yaw(), move.pitch());
            }
        }
        return new Anchor(0.5, 64.0, 0.5, 0f, 0f);
    }

    /** 基线缺少移动事件时补一段普通步行轨迹，避免注入信号没有载体。 */
    protected static List<Event.Move> vanillaMoves(CheatInjectionRequest request, Instant origin, int ticks) {
        List<Event.Move> moves = new ArrayList<>(ticks);
        double x = 0.5;
        for (int i = 0; i < ticks; i++) {
            moves.add(new Event.Move(request.playerId(), i, timestampAt(i, origin), request.platform(),
                    x, 64.0, 0.5, 0f, 0f, true, true, false));
            x += 0.215;
        }
        return moves;
    }

    /** 玩家位置与朝向。 */
    protected record Anchor(double x, double y, double z, float yaw, float pitch) {
    }
}
