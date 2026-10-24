package io.laplace.sprite.adapters.hybrid;

import io.laplace.sprite.core.model.PlatformFamily;

/**
 * 混合端的宿主编排（14.3 混合端组合适配器）。
 *
 * <p>混合端 = Bukkit 家族 × 模组加载器家族。Bukkit 侧统一按 Spigot API 归类，
 * 模组侧按各混合端的默认变体取值；Arclight 另有 NeoForge / Fabric 变体，由适配器自行声明。</p>
 */
public final class HybridHosts {

    private HybridHosts() {
    }

    /** 混合端默认挂载的模组加载器。 */
    public static PlatformFamily loaderFor(PlatformFamily hybrid) {
        return switch (hybrid) {
            case ARCLIGHT, MOHIST -> PlatformFamily.FORGE;
            case YOUER, MAGMA_NEO -> PlatformFamily.NEOFORGE;
            case CARDBOARD -> PlatformFamily.FABRIC;
            default -> throw new IllegalArgumentException("非混合端家族: " + hybrid);
        };
    }

    /** Bukkit 侧宿主家族（这些混合端都对外提供 Spigot 兼容 API）。 */
    public static PlatformFamily bukkitHost() {
        return PlatformFamily.SPIGOT;
    }
}
