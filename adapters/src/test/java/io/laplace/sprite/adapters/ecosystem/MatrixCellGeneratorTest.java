package io.laplace.sprite.adapters.ecosystem;

import com.fasterxml.jackson.databind.JsonNode;
import io.laplace.sprite.adapters.ecosystem.MatrixCellGenerator.CellDefinition;
import io.laplace.sprite.adapters.ecosystem.MatrixCellGenerator.DroppedCell;
import io.laplace.sprite.adapters.ecosystem.MatrixCellGenerator.GenerationResult;
import io.laplace.sprite.core.io.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 矩阵格自动生成器：裁剪、Java 版本维度与输出格式（14.9）。 */
class MatrixCellGeneratorTest {

    private final MatrixCellGenerator generator = MatrixCellGenerator.offline();

    @Test
    void pullRequestTierIsTheLatestLtsTimesLatestStableLoaderTimesBaseline() {
        GenerationResult result = generator.generate(GateTier.PR_SUBSET);

        assertTrue(result.dropped().isEmpty(), result.dropped().toString());
        assertEquals(3, result.cells().size(), "三个模组加载器各一格，PR 不含插件线");
        assertTrue(result.cells().stream().allMatch(cell -> "1.21.11".equals(cell.mc())));
        assertTrue(result.cells().stream().allMatch(cell -> cell.jdk() == 21));
        assertTrue(result.cells().stream().noneMatch(cell -> "paper".equals(cell.platform())));
        assertTrue(result.cells().stream()
                .allMatch(cell -> cell.bundles().equals(List.of(GateTier.BASELINE_BUNDLE))));
        assertEquals("0.19.5", cell("fabric/1.21.11/0.19.5", result.cells()).loader());
        assertEquals("21.11.0", cell("neoforge/1.21.11/21.11.0", result.cells()).loader());
        assertEquals("61.2.3", cell("forge/1.21.11/61.2.3", result.cells()).loader());
    }

    @Test
    void cellsWithoutBaselineBuildsAreDroppedWithAReason() {
        GenerationResult result = generator.generate(GateTier.WEEKLY_FULL);

        assertTrue(result.dropped().stream().anyMatch(drop -> isCell(drop, "fabric", "1.20.1")
                        && drop.reason().contains("sodium")),
                "Fabric 1.20.1 缺 Sodium 构建，应剪枝并写明原因");
        assertTrue(result.dropped().stream().anyMatch(drop -> isCell(drop, "forge", "1.20.1")
                        && drop.reason().contains("lithium")),
                "Forge 1.20.1 缺 Lithium 构建，应剪枝并写明原因");
        assertTrue(result.dropped().stream().anyMatch(drop -> isCell(drop, "neoforge", "1.20.1")));
        assertTrue(result.cells().stream()
                .noneMatch(cell -> "1.20.1".equals(cell.mc()) && !"paper".equals(cell.platform())));
        assertTrue(result.dropped().stream().allMatch(drop -> !drop.reason().isBlank()));
    }

    @Test
    void minecraft261CellsRequireJava25() {
        GenerationResult result = generator.generate(GateTier.DAILY);

        CellDefinition fabric = cell("fabric/26.1/0.20.0", result.cells());
        assertEquals(25, fabric.jdk(), "26.1 起强制 Java 25");
        CellDefinition paper = cell("paper/26.1", result.cells());
        assertEquals(25, paper.jdk());
        assertEquals(21, cell("fabric/1.21.11/0.19.5", result.cells()).jdk());
    }

    @Test
    void twentySixTwoCellsUseTheLatestLoadersAndJava25() {
        GenerationResult result = generator.generate(GateTier.DAILY);

        assertEquals(25, cell("fabric/26.2/0.21.0", result.cells()).jdk());
        assertEquals("26.2.0", cell("neoforge/26.2/26.2.0", result.cells()).loader());
        assertEquals("66.0.10", cell("forge/26.2/66.0.10", result.cells()).loader());
        assertTrue(result.cells().stream().anyMatch(cell -> "paper".equals(cell.platform())
                && "26.2".equals(cell.mc())));
    }

    @Test
    void optifineProbeNeverEntersTheGeneratedMatrix() {
        GenerationResult result = generator.generate(GateTier.WEEKLY_FULL);

        assertTrue(result.cells().stream().allMatch(cell ->
                cell.baselineMods().stream().noneMatch("optifine"::equals)));
        assertTrue(result.cells().stream().noneMatch(cell -> cell.bundles().contains("optifine")));
        assertFalse(ClosedSourceProbe.includedInAutomatedGate());
    }

    @Test
    void includeFragmentAndCellFileAreConsumable(@TempDir Path tempDir) throws IOException {
        GenerationResult result = generator.generate(GateTier.PR_SUBSET);

        String yaml = result.toIncludeFragment();
        assertTrue(yaml.startsWith("strategy:\n  fail-fast: false\n  max-parallel: 4\n"), yaml);
        assertTrue(yaml.contains("platform: fabric"), yaml);
        assertTrue(yaml.contains("mc: \"1.21.11\""), yaml);
        assertTrue(yaml.contains("bundles: [baseline-quartet]"), yaml);

        JsonNode json = Json.jsonMapper().readTree(result.toJson());
        assertEquals("pr", json.path("tier").asText());
        assertEquals(3, json.path("include").size());
        assertEquals("fabric/1.21.11/0.19.5", json.path("include").get(0).path("cell").asText());

        Path file = tempDir.resolve("cells").resolve("matrix-cells.json");
        result.writeCells(file);
        String written = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(written.contains("\"1.21.11\""), written);
        assertTrue(written.contains("\"baseline-quartet\""), written);
    }

    @Test
    void dailyAndWeeklyTiersWidenTheMatrix() {
        int pullRequest = generator.generate(GateTier.PR_SUBSET).cells().size();
        int daily = generator.generate(GateTier.DAILY).cells().size();
        int weekly = generator.generate(GateTier.WEEKLY_FULL).cells().size();

        assertTrue(daily > pullRequest);
        assertTrue(weekly >= daily);
        assertTrue(generator.generate(GateTier.WEEKLY_FULL).cells().stream()
                .anyMatch(cell -> cell.bundles().contains(GateTier.CONTENT_BUNDLE)));
        assertTrue(generator.generate(GateTier.WEEKLY_FULL).cells().stream()
                .anyMatch(cell -> cell.bundles().contains(GateTier.HEAVY_PLUGIN_BUNDLE)));
    }

    private static CellDefinition cell(String cellId, List<CellDefinition> cells) {
        return cells.stream().filter(cell -> cell.cellId().equals(cellId)).findFirst()
                .orElseThrow(() -> new AssertionError("缺少格: " + cellId + "，实际: "
                        + cells.stream().map(CellDefinition::cellId).toList()));
    }

    private static boolean isCell(DroppedCell drop, String platform, String mc) {
        return drop.cell().platform().equals(platform) && drop.cell().mc().equals(mc);
    }
}
