package io.laplace.sprite.cheats.inject;

import io.laplace.sprite.cheats.CheatInjectionRequest;
import io.laplace.sprite.cheats.CheatInjector;
import io.laplace.sprite.cheats.threshold.DetectionThresholdAnchor;
import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.model.SampleTier;

import java.time.Instant;
import java.util.Locale;
import java.util.Random;

/**
 * 作弊注入器骨架：在公共注入流程后追加客户端身份伪装。
 * 具体作弊行为由子类在 {@link #apply} 中实现。
 */
public abstract class AbstractCheatInjector extends AbstractSampleInjector implements CheatInjector {

    @Override
    public final EventStream inject(CheatInjectionRequest request) {
        InMemoryEventStream out = new InMemoryEventStream();
        SampleTier tier = resolveTier(request);
        apply(baselineEvents(request), out, request, tier);
        spoofIdentity(out, request, tier);
        return out;
    }

    /**
     * 本注入器主参数的键，取自该类型的检测阈值锚点；样本的主参数按「锚点 × 档位系数」派生，
     * 注入器据此读取，避免与锚点定义脱节。
     */
    protected final String primaryParamKey() {
        return DetectionThresholdAnchor.of(type()).paramKey();
    }

    /**
     * 隐蔽档位常伪造成原版或其它加载器身份；由档位参数 {@code spoofBrand} 控制。
     */
    private static void spoofIdentity(InMemoryEventStream out, CheatInjectionRequest request, SampleTier tier) {
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
}
