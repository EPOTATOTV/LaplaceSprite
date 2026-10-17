package io.laplace.sprite.adapters.ecosystem;

import io.laplace.sprite.adapters.ecosystem.BaselineFailureAttribution.Verdict;
import io.laplace.sprite.core.model.PlatformFamily;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 基线四件套、三条冒烟与失败归因（14.9）。 */
class BaselineBundleTest {

    private final BaselineFailureAttribution attribution = new BaselineFailureAttribution();

    @Test
    void fabricBundleIsTheQuartetWithoutIris() {
        BaselineBundle bundle = BaselineBundle.forLoader(PlatformFamily.FABRIC);

        assertEquals(List.of("fabric-api", "jei", "sodium", "lithium"), bundle.projectIds());
        assertFalse(bundle.includes(BaselineBundle.IRIS));
        assertTrue(bundle.mods().stream().noneMatch(mod -> mod.note().isBlank()), "每个基线包都要写为什么装");
    }

    @Test
    void clientProxyRuntimeAddsIrisAfterLithium() {
        BaselineBundle bundle = BaselineBundle.forLoader(PlatformFamily.FABRIC, true);

        assertEquals(List.of("fabric-api", "jei", "sodium", "lithium", "iris"), bundle.projectIds());
        assertTrue(bundle.includes(BaselineBundle.IRIS));
    }

    @Test
    void neoForgeAndForgeOnlyCarryJeiAndLithium() {
        assertEquals(List.of("jei", "lithium"),
                BaselineBundle.forLoader(PlatformFamily.NEOFORGE).projectIds());
        assertEquals(List.of("jei", "lithium"),
                BaselineBundle.forLoader(PlatformFamily.FORGE).projectIds());
        // 非模组线没有基线包可言，直接拒绝比静默返回空表好定位。
        assertThrows(IllegalArgumentException.class,
                () -> BaselineBundle.forLoader(PlatformFamily.PAPER));
    }

    @Test
    void threeBaselineAssertionsEncodeTheDocumentedThresholds() {
        List<BaselineSmoke.BaselineAssertion> assertions = BaselineSmoke.assertions();

        assertEquals(3, assertions.size());
        assertEquals(List.of(BaselineSmoke.Check.STARTUP_CLEAN,
                BaselineSmoke.Check.JEI_RECIPE_AND_REGISTRY,
                BaselineSmoke.Check.RENDER_PIPELINE_AND_FPS),
                assertions.stream().map(BaselineSmoke.BaselineAssertion::check).toList());
        assertEquals(0.20, BaselineSmoke.FPS_TOLERANCE);
        assertEquals(0.05, BaselineSmoke.ITEM_COUNT_TOLERANCE);
        assertTrue(assertions.get(2).threshold().contains("20%"), assertions.get(2).threshold());
        assertTrue(assertions.get(1).threshold().contains("5%"), assertions.get(1).threshold());
    }

    @Test
    void fpsToleranceIsTwoSided() {
        assertTrue(BaselineSmoke.fpsWithinTolerance(100, 80));
        assertTrue(BaselineSmoke.fpsWithinTolerance(100, 120));
        assertFalse(BaselineSmoke.fpsWithinTolerance(100, 79.9), "低于 -20% 判失败");
        assertFalse(BaselineSmoke.fpsWithinTolerance(100, 120.1), "高于 +20% 说明基线包可能没生效");
        assertFalse(BaselineSmoke.fpsWithinTolerance(0, 120));
    }

    @Test
    void itemRegistryToleranceIsRelative() {
        assertTrue(BaselineSmoke.itemCountWithinTolerance(1000, 950));
        assertTrue(BaselineSmoke.itemCountWithinTolerance(1000, 1050));
        assertFalse(BaselineSmoke.itemCountWithinTolerance(1000, 949));
        assertFalse(BaselineSmoke.itemCountWithinTolerance(1000, 1051));
        assertTrue(BaselineSmoke.itemCountWithinTolerance(0, 0));
    }

    @Test
    void controlCellReproductionBlamesTheBaseline() {
        BaselineFailureAttribution.CellOutcome control =
                BaselineFailureAttribution.CellOutcome.fail("baseline-only", "mixin apply failed: sodium");
        BaselineFailureAttribution.CellOutcome target =
                BaselineFailureAttribution.CellOutcome.fail("with-target", "mixin apply failed: sodium");

        assertEquals(Verdict.MOD_ITSELF, attribution.attribute(control, target));
        assertEquals(Verdict.MOD_ITSELF, attribution.attribute(true, false));
    }

    @Test
    void cleanControlBlamesTheTarget() {
        BaselineFailureAttribution.CellOutcome control =
                BaselineFailureAttribution.CellOutcome.pass("baseline-only");
        BaselineFailureAttribution.CellOutcome target =
                BaselineFailureAttribution.CellOutcome.fail("with-target", "mixin apply failed: target");

        assertEquals(Verdict.TARGET_UNDER_TEST, attribution.attribute(control, target));
        assertEquals(Verdict.NO_FAILURE, attribution.attribute(false, false));
    }
}
