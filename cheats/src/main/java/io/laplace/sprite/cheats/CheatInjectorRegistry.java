package io.laplace.sprite.cheats;

import io.laplace.sprite.cheats.inject.AutoClickerInjector;
import io.laplace.sprite.cheats.inject.AutoTotemInjector;
import io.laplace.sprite.cheats.inject.BlinkInjector;
import io.laplace.sprite.cheats.inject.FastEatInjector;
import io.laplace.sprite.cheats.inject.FastPlaceInjector;
import io.laplace.sprite.cheats.inject.FlyInjector;
import io.laplace.sprite.cheats.inject.JesusInjector;
import io.laplace.sprite.cheats.inject.KillAuraInjector;
import io.laplace.sprite.cheats.inject.NoFallInjector;
import io.laplace.sprite.cheats.inject.PhaseInjector;
import io.laplace.sprite.cheats.inject.ReachInjector;
import io.laplace.sprite.cheats.inject.ScaffoldInjector;
import io.laplace.sprite.cheats.inject.SpeedInjector;
import io.laplace.sprite.cheats.inject.TimerInjector;
import io.laplace.sprite.cheats.inject.XrayInjector;
import io.laplace.sprite.core.model.CheatType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 注入器注册表：按作弊类型取注入器，覆盖 {@link CheatType} 全部取值。
 */
public final class CheatInjectorRegistry {

    private final Map<CheatType, CheatInjector> injectors = new EnumMap<>(CheatType.class);

    public CheatInjectorRegistry() {
        List<CheatInjector> all = List.of(
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
        for (CheatInjector injector : all) {
            if (injectors.put(injector.type(), injector) != null) {
                throw new IllegalStateException("注入器重复注册: " + injector.type());
            }
        }
    }

    public CheatInjector of(CheatType type) {
        CheatInjector injector = injectors.get(type);
        if (injector == null) {
            throw new IllegalArgumentException("未注册注入器: " + type);
        }
        return injector;
    }

    public Set<CheatType> supported() {
        return Set.copyOf(injectors.keySet());
    }
}
