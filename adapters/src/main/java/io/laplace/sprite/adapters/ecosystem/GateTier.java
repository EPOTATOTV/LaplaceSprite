package io.laplace.sprite.adapters.ecosystem;

import java.util.List;
import java.util.Locale;

/**
 * 门禁三级收敛（14.9）。
 *
 * <ol>
 *   <li>PR 子集：最新 1 个 MC LTS × 最新稳定加载器 × 基线四件套；</li>
 *   <li>每日：近 3 个 MC 大版本 × 全加载器中间子集；</li>
 *   <li>每周全量：全部版本与加载器，含 Create / Alex's Mobs / ViaVersion 重型包。</li>
 * </ol>
 *
 * <p>PR 的 {@code fail-fast: false} 与 {@code max-parallel: 4} 是文档写死的，其余档沿用同一并发度。</p>
 */
public enum GateTier {

    PR_SUBSET("pr", 1, true, false, false, 4),
    DAILY("daily", 3, false, false, true, 4),
    WEEKLY_FULL("weekly", Integer.MAX_VALUE, false, true, true, 4);

    public static final String BASELINE_BUNDLE = "baseline-quartet";
    public static final String TOP_PLUGIN_BUNDLE = "top-plugins";
    public static final String CONTENT_BUNDLE = "content-create-alex";
    public static final String HEAVY_PLUGIN_BUNDLE = "heavy-plugins";

    private final String cliName;
    private final int minecraftVersionLimit;
    private final boolean latestLtsOnly;
    private final boolean includesHeavyBundles;
    private final boolean includesPluginLine;
    private final int maxParallel;

    GateTier(String cliName, int minecraftVersionLimit, boolean latestLtsOnly,
             boolean includesHeavyBundles, boolean includesPluginLine, int maxParallel) {
        this.cliName = cliName;
        this.minecraftVersionLimit = minecraftVersionLimit;
        this.latestLtsOnly = latestLtsOnly;
        this.includesHeavyBundles = includesHeavyBundles;
        this.includesPluginLine = includesPluginLine;
        this.maxParallel = maxParallel;
    }

    public String cliName() {
        return cliName;
    }

    /** 该档取几个 Minecraft 版本；每周档取全部。 */
    public int minecraftVersionLimit() {
        return minecraftVersionLimit;
    }

    /** PR 只取最新一个 LTS，避免把非 LTS 快照塞进门禁。 */
    public boolean latestLtsOnly() {
        return latestLtsOnly;
    }

    public boolean includesHeavyBundles() {
        return includesHeavyBundles;
    }

    /** PR 只跑基线四件套这条客户端线，插件线留给每日与每周。 */
    public boolean includesPluginLine() {
        return includesPluginLine;
    }

    public int maxParallel() {
        return maxParallel;
    }

    public boolean failFast() {
        return false;
    }

    /**
     * 某条线要注入的包。
     *
     * @param pluginPlatform 插件线（Paper 等）为 true，模组线为 false
     */
    public List<String> bundlesFor(boolean pluginPlatform) {
        return switch (this) {
            case PR_SUBSET -> List.of(BASELINE_BUNDLE);
            case DAILY -> pluginPlatform
                    ? List.of(BASELINE_BUNDLE, TOP_PLUGIN_BUNDLE)
                    : List.of(BASELINE_BUNDLE);
            case WEEKLY_FULL -> pluginPlatform
                    ? List.of(BASELINE_BUNDLE, TOP_PLUGIN_BUNDLE, HEAVY_PLUGIN_BUNDLE)
                    : List.of(BASELINE_BUNDLE, CONTENT_BUNDLE);
        };
    }

    public static GateTier fromCliName(String name) {
        String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        for (GateTier tier : values()) {
            if (tier.cliName.equals(normalized)) {
                return tier;
            }
        }
        throw new IllegalArgumentException("未知门禁档位: " + name + "（可选: pr / daily / weekly）");
    }
}
