package io.laplace.sprite.adapters;

import io.laplace.sprite.adapters.matrix.AffectedFamilySelector;
import io.laplace.sprite.adapters.matrix.AffectedFamilySelector.Selection;
import io.laplace.sprite.adapters.matrix.FamilyTier;
import io.laplace.sprite.adapters.matrix.MatrixPlan;
import io.laplace.sprite.adapters.matrix.MatrixPlan.MatrixJob;
import io.laplace.sprite.adapters.matrix.VersionMatrixRegistry;
import io.laplace.sprite.core.model.PlatformFamily;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 家族分档、受影响族选择与 CI 矩阵计划（14.7）。 */
class MatrixPlanTest {

    private static final String BUKKIT_PATH =
            "adapters/src/main/java/io/laplace/sprite/adapters/bukkit/PaperAdapter.java";
    private static final String DESCRIPTOR_PATH =
            "adapters/src/main/java/io/laplace/sprite/adapters/descriptor/PluginYmlParser.java";

    private final AffectedFamilySelector selector = new AffectedFamilySelector();

    @Test
    void tierMappingFollowsTheAdapterMatrix() {
        assertEquals(FamilyTier.TIER1, FamilyTier.of(PlatformFamily.PAPER));
        assertEquals(FamilyTier.TIER1, FamilyTier.of(PlatformFamily.FOLIA));
        assertEquals(FamilyTier.TIER1, FamilyTier.of(PlatformFamily.SPONGE_NEO));
        assertEquals(FamilyTier.TIER1, FamilyTier.of(PlatformFamily.NEOFORGE));
        assertEquals(FamilyTier.TIER1, FamilyTier.of(PlatformFamily.ARCLIGHT));
        assertEquals(FamilyTier.TIER2, FamilyTier.of(PlatformFamily.FORGE));
        assertEquals(FamilyTier.TIER2, FamilyTier.of(PlatformFamily.MAGMA_NEO));
        assertEquals(FamilyTier.TIER2, FamilyTier.of(PlatformFamily.CARDBOARD));
        assertEquals(FamilyTier.TIER2, FamilyTier.of(PlatformFamily.MOHIST));
        assertEquals(FamilyTier.EXCLUDED, FamilyTier.of(PlatformFamily.WATERFALL));
        assertFalse(FamilyTier.of(PlatformFamily.WATERFALL).gatesPullRequest());
        assertTrue(FamilyTier.of(PlatformFamily.PAPER).gatesPullRequest());
    }

    @Test
    void bukkitChangeSelectsOnlyTheBukkitFamily() {
        Selection selection = selector.select(List.of(BUKKIT_PATH));

        assertEquals(Set.of(PlatformFamily.BUKKIT, PlatformFamily.SPIGOT, PlatformFamily.PAPER,
                PlatformFamily.PURPUR, PlatformFamily.PUFFERFISH, PlatformFamily.FOLIA),
                selection.families());
        assertFalse(selection.fullMatrixRequired());
        assertTrue(selection.reason().contains("bukkit/"), selection.reason());
    }

    @Test
    void descriptorParserChangeSelectsEveryFamilyItServes() {
        Selection selection = selector.select(List.of(DESCRIPTOR_PATH));

        assertTrue(selection.families().containsAll(Set.of(PlatformFamily.BUKKIT, PlatformFamily.SPIGOT,
                PlatformFamily.PURPUR, PlatformFamily.PUFFERFISH, PlatformFamily.FOLIA,
                PlatformFamily.NUKKIT, PlatformFamily.POWER_NUKKIT_X, PlatformFamily.GEYSER)),
                selection.families().toString());
        assertFalse(selection.families().contains(PlatformFamily.FABRIC), "Fabric 用 fabric.mod.json");
        assertFalse(selection.fullMatrixRequired());
    }

    @Test
    void sharedCodeFallsBackToAllFamilies() {
        Selection core = selector.select(
                List.of("core/src/main/java/io/laplace/sprite/core/spi/AdapterRegistry.java"));
        assertEquals(Set.of(PlatformFamily.values()), core.families());
        assertTrue(core.fullMatrixRequired());

        Selection sharedHelper = selector.select(
                List.of("adapters/src/main/java/io/laplace/sprite/adapters/lifecycle/SimulatedLifecycleHook.java"));
        assertEquals(Set.of(PlatformFamily.values()), sharedHelper.families());
        assertTrue(sharedHelper.fullMatrixRequired());

        Selection descriptorCommon = selector.select(
                List.of("adapters/src/main/java/io/laplace/sprite/adapters/descriptor/DescriptorParserRegistry.java"));
        assertTrue(descriptorCommon.fullMatrixRequired());
    }

    @Test
    void unknownOrMissingPathsAreTreatedConservatively() {
        Selection unknown = selector.select(List.of("mystery/integration.kt"));
        assertEquals(Set.of(PlatformFamily.values()), unknown.families());
        assertTrue(unknown.fullMatrixRequired(), "存疑时一律按全家族处理");

        Selection empty = selector.select(List.of());
        assertEquals(Set.of(PlatformFamily.values()), empty.families());
        assertTrue(empty.fullMatrixRequired(), "没有变更路径就无从判定");
    }

    @Test
    void docsAndCiChangesSelectNothing() {
        Selection selection = selector.select(List.of(
                "docs/14-adapters.md", ".github/workflows/adapter-matrix.yml", "README.md"));

        assertTrue(selection.empty());
        assertFalse(selection.fullMatrixRequired());
    }

    @Test
    void pullRequestPlanOnlyContainsFirstTierFamilies() {
        Selection selection = selector.select(
                List.of("adapters/src/main/java/io/laplace/sprite/adapters/hybrid/CardboardAdapter.java"));

        MatrixPlan plan = MatrixPlan.forPullRequest(selection, VersionMatrixRegistry.defaults());

        assertFalse(plan.fullMatrix());
        assertTrue(plan.jobs().stream()
                .allMatch(job -> FamilyTier.of(job.family()) == FamilyTier.TIER1));
        assertTrue(plan.jobs().stream().noneMatch(job -> job.family() == PlatformFamily.CARDBOARD));
        assertEquals(Set.of(PlatformFamily.ARCLIGHT, PlatformFamily.YOUER),
                plan.jobs().stream().map(MatrixJob::family).collect(Collectors.toSet()));
    }

    @Test
    void bukkitChangeKeepsOtherLinesOutOfThePullRequestPlan() {
        MatrixPlan plan = MatrixPlan.forPullRequest(selector.select(List.of(BUKKIT_PATH)),
                VersionMatrixRegistry.defaults());

        assertEquals(6, plan.jobs().size(), "Bukkit 家族 6 个平台各一条");
        assertTrue(plan.jobs().stream().noneMatch(job -> job.family().isModLoader()));
        assertTrue(plan.jobs().stream().allMatch(job -> job.jobLine().equals(MatrixPlan.JOB_LINE_DEFAULT)));
    }

    @Test
    void nightlyPlanCoversTheWholeMatrixWithSeparateJdkLines() {
        MatrixPlan plan = MatrixPlan.forNightly(VersionMatrixRegistry.defaults());

        assertTrue(plan.fullMatrix());
        Set<PlatformFamily> families = plan.jobs().stream().map(MatrixJob::family).collect(Collectors.toSet());
        assertTrue(families.contains(PlatformFamily.FORGE));
        assertTrue(families.contains(PlatformFamily.CARDBOARD));
        assertFalse(families.contains(PlatformFamily.WATERFALL), "EOL 家族不进矩阵");
        assertFalse(families.contains(PlatformFamily.MODPACK));

        assertTrue(plan.jobs().stream().filter(job -> job.jobLine().equals(MatrixPlan.JOB_LINE_BEDROCK))
                .allMatch(job -> job.jdk() == 21));
        assertTrue(plan.jobs().stream().filter(job -> job.jobLine().equals(MatrixPlan.JOB_LINE_PROXY))
                .allMatch(job -> job.jdk() == 17));
        assertTrue(plan.jobs().stream().filter(job -> job.family() == PlatformFamily.FABRIC).count() >= 5,
                "夜间模组线按矩阵所有档位展开");
    }

    @Test
    void matrixYamlIsAConsumableIncludeFragment() {
        MatrixPlan plan = MatrixPlan.forPullRequest(selector.select(
                List.of("adapters/src/main/java/io/laplace/sprite/adapters/modloader/FabricAdapter.java")),
                VersionMatrixRegistry.defaults());

        String yaml = plan.toMatrixYaml();

        assertTrue(yaml.startsWith("strategy:\n  fail-fast: false\n  matrix:\n    include:\n"), yaml);
        assertTrue(yaml.contains("- { platform: fabric, mc: \"1.21.11\", loader: \"0.19.5\", adapter: fabric"),
                yaml);
    }

    @Test
    void docsOnlyChangeYieldsAnEmptyInclude() {
        MatrixPlan plan = MatrixPlan.forPullRequest(selector.select(List.of("docs/readme.md")),
                VersionMatrixRegistry.defaults());

        assertTrue(plan.jobs().isEmpty());
        assertTrue(plan.toMatrixYaml().contains("include: []"), plan.toMatrixYaml());
    }
}
