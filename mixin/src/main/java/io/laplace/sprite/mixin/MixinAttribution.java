package io.laplace.sprite.mixin;

import java.util.List;
import java.util.Objects;

/**
 * 一条 Mixin Watchdog 归因（15.11 P1）：「哪个 mod 改了哪个类」。
 *
 * @param modId       模组 id
 * @param targetClass 被织入的 MC 类
 * @param mixins      该 mod 作用在此类上的 mixin 类名
 */
public record MixinAttribution(String modId, String targetClass, List<String> mixins) {

    public MixinAttribution {
        Objects.requireNonNull(modId, "modId");
        Objects.requireNonNull(targetClass, "targetClass");
        mixins = mixins == null ? List.of() : List.copyOf(mixins);
    }
}
