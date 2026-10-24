package io.laplace.sprite.core.model;

/**
 * 平台/加载器家族。第十四章按 API 家族分组，而非逐平台建分支。
 */
public enum PlatformFamily {
    // Bukkit 家族
    BUKKIT, SPIGOT, PAPER, PURPUR, PUFFERFISH, FOLIA,
    // 代理端家族
    BUNGEECORD, WATERFALL, VELOCITY,
    // 基岩家族
    NUKKIT, POWER_NUKKIT_X, ALLAY, GEYSER,
    // 模组加载器家族
    FORGE, NEOFORGE, FABRIC, QUILT, SPONGE_VANILLA, SPONGE_NEO, SPONGE_FORGE,
    // 混合端（Bukkit × 模组加载器组合适配器）
    ARCLIGHT, YOUER, MOHIST, MAGMA_NEO, CARDBOARD,
    // 非加载器产物
    VANILLA_DATAPACK, MODPACK;

    public boolean isPluginPlatform() {
        return switch (this) {
            case BUKKIT, SPIGOT, PAPER, PURPUR, PUFFERFISH, FOLIA,
                 BUNGEECORD, WATERFALL, VELOCITY,
                 NUKKIT, POWER_NUKKIT_X, ALLAY -> true;
            default -> false;
        };
    }

    public boolean isProxy() {
        return this == BUNGEECORD || this == WATERFALL || this == VELOCITY;
    }

    public boolean isBedrock() {
        return this == NUKKIT || this == POWER_NUKKIT_X || this == ALLAY || this == GEYSER;
    }

    public boolean isModLoader() {
        return switch (this) {
            case FORGE, NEOFORGE, FABRIC, QUILT, SPONGE_VANILLA, SPONGE_NEO, SPONGE_FORGE -> true;
            default -> false;
        };
    }

    /** Sponge 系：装配在 mods/，用 sponge_plugins.json 识别，自有 @Listener 事件总线。 */
    public boolean isSponge() {
        return this == SPONGE_VANILLA || this == SPONGE_NEO || this == SPONGE_FORGE;
    }

    /** 混合端：Bukkit 家族 × 模组加载器的组合适配器，双目录双事件总线。 */
    public boolean isHybrid() {
        return this == ARCLIGHT || this == YOUER || this == MOHIST || this == MAGMA_NEO
                || this == CARDBOARD;
    }

    /**
     * 兼容链：矩阵不匹配时的回退目标，按「同族就近」排序（14.4）。
     *
     * <p>混合端回退到纯 Bukkit 或纯模组端；Sponge 系回退到宿主加载器。</p>
     */
    public PlatformFamily[] compatChain() {
        return switch (this) {
            case PURPUR, PUFFERFISH -> new PlatformFamily[]{PAPER, SPIGOT, BUKKIT};
            case FOLIA -> new PlatformFamily[]{PAPER, BUKKIT};
            case PAPER -> new PlatformFamily[]{SPIGOT, BUKKIT};
            case SPIGOT -> new PlatformFamily[]{BUKKIT};
            case WATERFALL -> new PlatformFamily[]{BUNGEECORD};
            case QUILT -> new PlatformFamily[]{FABRIC};
            case POWER_NUKKIT_X -> new PlatformFamily[]{NUKKIT};
            case NEOFORGE -> new PlatformFamily[]{FORGE};
            case GEYSER -> new PlatformFamily[]{PAPER};
            case SPONGE_FORGE -> new PlatformFamily[]{FORGE};
            case SPONGE_NEO -> new PlatformFamily[]{NEOFORGE};
            case SPONGE_VANILLA -> new PlatformFamily[]{};
            case ARCLIGHT, YOUER -> new PlatformFamily[]{PAPER, FORGE, NEOFORGE, FABRIC};
            case MOHIST -> new PlatformFamily[]{PAPER, FORGE};
            case MAGMA_NEO -> new PlatformFamily[]{NEOFORGE};
            case CARDBOARD -> new PlatformFamily[]{FABRIC, PAPER};
            default -> new PlatformFamily[0];
        };
    }
}
