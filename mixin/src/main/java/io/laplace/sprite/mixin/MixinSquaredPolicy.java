package io.laplace.sprite.mixin;

import io.laplace.sprite.core.model.PlatformFamily;

import java.util.Objects;

/**
 * MixinSquared 跨模组织入调解策略（15.11 P2）。
 *
 * <p>只对 Forge / NeoForge 启用。MixinSquared 以 JarInJar 内嵌库分发（随模组 jar 一起打包，
 * 不单独安装），用来把 Mixin 织进其他模组的 Mixin 并调解冲突排序。Fabric 不启用：
 * 原生 Mixin 0.8.x 的排序语义与 MixinSquared 的调解规则不同，强行注入会改变既有加载顺序。</p>
 */
public final class MixinSquaredPolicy {

    public static final String DISTRIBUTION_NOTE =
            "MixinSquared 以 JarInJar 内嵌库分发：随 Forge/NeoForge 模组打进同一个 jar，放在 META-INF/jarjar/ 下，不单独投放到 mods/。";

    private final PlatformFamily family;
    private final boolean enabled;
    private final String reason;

    private MixinSquaredPolicy(PlatformFamily family, boolean enabled, String reason) {
        this.family = Objects.requireNonNull(family, "family");
        this.enabled = enabled;
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public static MixinSquaredPolicy forFamily(PlatformFamily family) {
        Objects.requireNonNull(family, "family");
        return switch (family) {
            case FORGE, NEOFORGE -> new MixinSquaredPolicy(family, true,
                    "Forge/NeoForge 矩阵默认注入 MixinSquared，调解跨模组织入冲突并固定排序");
            case FABRIC, QUILT -> new MixinSquaredPolicy(family, false,
                    "Fabric 侧原生 Mixin 0.8.x 排序语义不同，启用 MixinSquared 会改变既有顺序，故不启用");
            default -> new MixinSquaredPolicy(family, false,
                    family + " 不是 Forge/NeoForge 家族，不适用 MixinSquared");
        };
    }

    public PlatformFamily family() {
        return family;
    }

    public boolean enabled() {
        return enabled;
    }

    public String reason() {
        return reason;
    }

    /** JarInJar 分发说明，写进兼容性报告。 */
    public String distributionNote() {
        return DISTRIBUTION_NOTE;
    }
}
