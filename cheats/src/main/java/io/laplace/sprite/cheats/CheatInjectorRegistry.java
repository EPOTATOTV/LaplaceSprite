package io.laplace.sprite.cheats;

import io.laplace.sprite.cheats.inject.AnomalyInjector;
import io.laplace.sprite.cheats.inject.AutoClickerInjector;
import io.laplace.sprite.cheats.inject.AutoTotemInjector;
import io.laplace.sprite.cheats.inject.BlinkInjector;
import io.laplace.sprite.cheats.inject.ContentAnomalyInjector;
import io.laplace.sprite.cheats.inject.FastEatInjector;
import io.laplace.sprite.cheats.inject.FastPlaceInjector;
import io.laplace.sprite.cheats.inject.FlyInjector;
import io.laplace.sprite.cheats.inject.JesusInjector;
import io.laplace.sprite.cheats.inject.KillAuraInjector;
import io.laplace.sprite.cheats.inject.NetworkAnomalyInjector;
import io.laplace.sprite.cheats.inject.NoFallInjector;
import io.laplace.sprite.cheats.inject.PerfAnomalyInjector;
import io.laplace.sprite.cheats.inject.PhaseInjector;
import io.laplace.sprite.cheats.inject.ReachInjector;
import io.laplace.sprite.cheats.inject.ScaffoldInjector;
import io.laplace.sprite.cheats.inject.SpeedInjector;
import io.laplace.sprite.cheats.inject.TechAnomalyInjector;
import io.laplace.sprite.cheats.inject.TimerInjector;
import io.laplace.sprite.cheats.inject.XrayInjector;
import io.laplace.sprite.core.model.CheatType;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 注入器注册表：作弊注入器按 {@link CheatType} 索引，通用异常注入器按样本 YAML 的
 * {@code injector} id 索引。
 */
public final class CheatInjectorRegistry {

    private final Map<CheatType, CheatInjector> injectors = new EnumMap<>(CheatType.class);
    private final Map<String, SampleInjector> byInjectorId = new LinkedHashMap<>();

    public CheatInjectorRegistry() {
        List<CheatInjector> cheats = List.of(
                new SpeedInjector(),
                new FlyInjector(),
                new ReachInjector(),
                new AutoClickerInjector(),
                new KillAuraInjector(),
                new TimerInjector(),
                new BlinkInjector(),
                new NoFallInjector(),
                new PhaseInjector(),
                new JesusInjector(),
                new ScaffoldInjector(),
                new FastPlaceInjector(),
                new FastEatInjector(),
                new AutoTotemInjector(),
                new XrayInjector());
        for (CheatInjector injector : cheats) {
            if (injectors.put(injector.type(), injector) != null) {
                throw new IllegalStateException("注入器重复注册: " + injector.type());
            }
            register(injector);
        }
        register(new ContentAnomalyInjector());
        register(new TechAnomalyInjector());
        register(new PerfAnomalyInjector());
        register(new NetworkAnomalyInjector());
        register(new AnomalyInjector());
    }

    private void register(SampleInjector injector) {
        if (byInjectorId.put(injector.id(), injector) != null) {
            throw new IllegalStateException("注入器 id 重复: " + injector.id());
        }
    }

    public CheatInjector of(CheatType type) {
        CheatInjector injector = injectors.get(type);
        if (injector == null) {
            throw new IllegalArgumentException("未注册注入器: " + type);
        }
        return injector;
    }

    public SampleInjector byId(String id) {
        SampleInjector injector = byInjectorId.get(id);
        if (injector == null) {
            throw new IllegalArgumentException("未注册注入器: " + id);
        }
        return injector;
    }

    /** 按样本声明的 injector 取；未声明时按类别兜底。 */
    public SampleInjector forSpec(SampleSpec spec) {
        String id = spec.injector() == null || spec.injector().isBlank()
                ? defaultId(spec.category())
                : spec.injector();
        return byId(id);
    }

    public Set<CheatType> supported() {
        return Set.copyOf(injectors.keySet());
    }

    public Set<String> supportedIds() {
        return Set.copyOf(byInjectorId.keySet());
    }

    private static String defaultId(SampleCategory category) {
        return switch (category) {
            case CONTENT -> "content";
            case TECH -> "tech";
            case PERF -> "perf";
            case NETWORK -> "network";
            case ANOMALY -> "anomaly";
            case CHEAT -> throw new IllegalArgumentException("作弊样本必须声明 injector");
        };
    }
}
