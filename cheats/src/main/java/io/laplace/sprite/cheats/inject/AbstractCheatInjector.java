package io.laplace.sprite.cheats.inject;

import io.laplace.sprite.cheats.CheatInjectionRequest;
import io.laplace.sprite.cheats.CheatInjector;
import io.laplace.sprite.cheats.threshold.DetectionThresholdAnchor;
import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.model.CheatTier;
import io.laplace.sprite.core.model.CheatTierName;
import io.laplace.sprite.core.model.ExpectedDetection;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * 注入器公共骨架：复制合法基线、解析档位参数、追加客户端身份伪装。
 * 具体作弊行为由子类在 {@link #apply} 中实现。
 */
public abstract class AbstractCheatInjector implements CheatInjector {

    /** 服务端一 tick 的基准时长（20TPS）。 */
    protected static final long TICK_MILLIS = 50L;

    @Override
    public final EventStream inject(CheatInjectionRequest request) {
        List<Event> baseline = baselineEvents(request);
        CheatTier tier = resolveTier(request);
        InMemoryEventStream out = new InMemoryEventStream();
        apply(baseline, out, request, tier);
        spoofIdentity(out, request, tier);
        return out;
    }

    /** 基于合法基线写出作弊事件流。 */
    protected abstract void apply(List<Event> baseline, InMemoryEventStream out,
                                  CheatInjectionRequest request, CheatTier tier);

    protected static List<Event> baselineEvents(CheatInjectionRequest request) {
        EventStream baseline = request.baseline();
        return baseline == null ? List.of() : baseline.events();
    }

    protected static CheatTier resolveTier(CheatInjectionRequest request) {
        return request.spec().tier(request.tier())
                .or(() -> request.spec().tier(CheatTierName.DEFAULT))
                .orElseGet(() -> new CheatTier(request.tier(), Map.of(), ExpectedDetection.detected()));
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

    protected static String paramString(CheatTier tier, String key, String fallback) {
        Object value = tier.params().get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    /**
     * 本注入器主参数的键，取自该类型的检测阈值锚点；样本的主参数按「锚点 × 档位系数」派生，
     * 注入器据此读取，避免与锚点定义脱节。
     */
    protected final String primaryParamKey() {
        return DetectionThresholdAnchor.of(type()).paramKey();
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

    /** 基线缺少移动事件时补一段普通步行轨迹，避免作弊信号没有载体。 */
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

    /**
     * 隐蔽档位常伪造成原版或其它加载器身份；由档位参数 {@code spoofBrand} 控制。
     */
    private static void spoofIdentity(InMemoryEventStream out, CheatInjectionRequest request, CheatTier tier) {
        if (!tier.boolParam("spoofBrand", false)) {
            return;
        }
        Instant origin = originOf(request.baseline());
        long tick = lastTick(out.events()) + 1;
        Instant timestamp = timestampAt(tick, origin);
        String brand = paramString(tier, "brand", "fabric");
        String modId = paramString(tier, "modId", defaultModId(request));
        out.emit(new Event.Brand(request.playerId(), tick, timestamp, request.platform(), brand));
        out.emit(new Event.ModHash(request.playerId(), tick, timestamp, request.platform(), modId,
                fakeHash(request.seed(), modId)));
        out.emit(new Event.Addon(request.playerId(), tick, timestamp, request.platform(), modId, "1.0"));
    }

    private static String defaultModId(CheatInjectionRequest request) {
        String source = request.spec().source();
        return source == null ? "unknown" : source.toLowerCase(Locale.ROOT);
    }

    private static String fakeHash(long seed, String modId) {
        Random random = new Random(seed ^ (long) modId.hashCode());
        StringBuilder hex = new StringBuilder(64);
        for (int i = 0; i < 32; i++) {
            hex.append(String.format("%02x", random.nextInt(256)));
        }
        return hex.toString();
    }

    /** 玩家位置与朝向。 */
    protected record Anchor(double x, double y, double z, float yaw, float pitch) {
    }
}
