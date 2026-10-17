package io.laplace.sprite.adapters.matrix;

import io.laplace.sprite.core.model.PlatformFamily;

/**
 * 家族分档（14.2 / 14.3「适配器状态」列 + 14.7 组合爆炸控制）。
 *
 * <p>只影响 CI 该跑什么，不影响运行时能力。</p>
 */
public enum FamilyTier {

    /** 第一梯队：进 PR 门禁。 */
    TIER1,
    /** 第二梯队：只进夜间全矩阵。 */
    TIER2,
    /** 排除：从不进 CI 矩阵。 */
    EXCLUDED;

    public boolean gatesPullRequest() {
        return this == TIER1;
    }

    /**
     * 家族到分档的映射，依据全部来自文档结论，不按流行度猜：
     *
     * <ul>
     *   <li>TIER1：14.2 标「已实现 · 第一梯队」的 Bukkit / Paper，及必须随族跑的 Spigot（同族复用）、
     *       Purpur / Pufferfish（Paper 兼容壳）、Folia（并发变体分支）；14.7 明确要给
     *       基岩线（Nukkit / PowerNukkitX / Allay）与代理线（BungeeCord / Velocity）各出独立 job，
     *       Geyser 随宿主挂载所以并入基岩线；14.3 标「新增 · 第一梯队」的 Fabric / NeoForge 与
     *       Sponge 系，Quilt 是 Fabric 兼容复用；混合端「仅第一梯队进 CI 门禁」，即 Arclight 与 Youer。</li>
     *   <li>TIER2：Forge（14.3 标「兼容分支」，生态重心已迁 NeoForge）、Magma-Neo（未进混合端第一梯队）、
     *       Cardboard（14.3 标「仅验证」）。</li>
     *   <li>EXCLUDED：Waterfall（14.2 标「EOL · 不投入新开发」）、以及不是服务端家族、
     *       在接入矩阵里没有行的 VANILLA_DATAPACK / MODPACK。</li>
     * </ul>
     */
    public static FamilyTier of(PlatformFamily family) {
        return switch (family) {
            case BUKKIT, SPIGOT, PAPER, PURPUR, PUFFERFISH, FOLIA,
                 BUNGEECORD, VELOCITY,
                 NUKKIT, POWER_NUKKIT_X, ALLAY, GEYSER,
                 FABRIC, NEOFORGE, QUILT,
                 SPONGE_VANILLA, SPONGE_NEO, SPONGE_FORGE,
                 ARCLIGHT, YOUER -> TIER1;
            case FORGE, MAGMA_NEO, CARDBOARD -> TIER2;
            case WATERFALL, VANILLA_DATAPACK, MODPACK -> EXCLUDED;
        };
    }
}
