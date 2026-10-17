package io.laplace.sprite.adapters.ecosystem;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 门禁三级收敛的组成（14.9）。 */
class GateTierTest {

    @Test
    void pullRequestRunsOneLatestLtsWithBaselineOnly() {
        GateTier tier = GateTier.PR_SUBSET;

        assertTrue(tier.latestLtsOnly());
        assertEquals(1, tier.minecraftVersionLimit());
        assertEquals(List.of(GateTier.BASELINE_BUNDLE), tier.bundlesFor(false));
        assertEquals(List.of(GateTier.BASELINE_BUNDLE), tier.bundlesFor(true));
        assertEquals(4, tier.maxParallel(), "PR 固定 max-parallel: 4");
        assertFalse(tier.failFast(), "PR 固定 fail-fast: false");
        assertFalse(tier.includesHeavyBundles());
        assertFalse(tier.includesPluginLine(), "PR 只跑基线四件套，不跑插件线");
    }

    @Test
    void dailyRunsThreeMinecraftVersionsAcrossAllLoaders() {
        GateTier tier = GateTier.DAILY;

        assertFalse(tier.latestLtsOnly());
        assertEquals(3, tier.minecraftVersionLimit());
        assertEquals(List.of(GateTier.BASELINE_BUNDLE), tier.bundlesFor(false));
        assertEquals(List.of(GateTier.BASELINE_BUNDLE, GateTier.TOP_PLUGIN_BUNDLE), tier.bundlesFor(true));
        assertTrue(tier.includesPluginLine());
        assertFalse(tier.includesHeavyBundles());
    }

    @Test
    void weeklyAddsContentAndHeavyBundles() {
        GateTier tier = GateTier.WEEKLY_FULL;

        assertTrue(tier.includesHeavyBundles());
        assertTrue(tier.includesPluginLine());
        assertEquals(Integer.MAX_VALUE, tier.minecraftVersionLimit());
        assertEquals(List.of(GateTier.BASELINE_BUNDLE, GateTier.CONTENT_BUNDLE), tier.bundlesFor(false));
        assertEquals(List.of(GateTier.BASELINE_BUNDLE, GateTier.TOP_PLUGIN_BUNDLE,
                GateTier.HEAVY_PLUGIN_BUNDLE), tier.bundlesFor(true));
    }

    @Test
    void cliNamesRoundTrip() {
        assertEquals(GateTier.PR_SUBSET, GateTier.fromCliName("pr"));
        assertEquals(GateTier.DAILY, GateTier.fromCliName(" DAILY "));
        assertEquals(GateTier.WEEKLY_FULL, GateTier.fromCliName("weekly"));
        assertThrows(IllegalArgumentException.class, () -> GateTier.fromCliName("nightly"));
    }
}
