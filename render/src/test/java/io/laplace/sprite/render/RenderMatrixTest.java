package io.laplace.sprite.render;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 渲染后端矩阵（15.12 P1，v2 L2-02）：Vulkan 按版本进 diff、容差按管线分档。 */
class RenderMatrixTest {

    @Test
    void defaultsCoverThreeBackendsForEachMinecraftVersion() {
        RenderMatrix matrix = RenderMatrix.defaults();

        assertEquals(15, matrix.size());
        assertEquals(15, matrix.cells().size());
        assertEquals(5, matrix.mcVersions().size());
        assertEquals(3, matrix.backends().size());
        assertEquals(List.of("26.4", "26.3", "26.2", "26.1", "1.21.1"), matrix.mcVersions());
    }

    @Test
    void vulkanModJoinsPixelDiffOnlyFromTwentySixFour() {
        for (RenderMatrixCell cell : RenderMatrix.defaults().cells()) {
            if (cell.backend() == RenderBackend.VULKANMOD) {
                if (RenderPipeline.vulkanDefault(cell.mcVersion())) {
                    assertTrue(cell.diffEnabled(), "26.4 起 Vulkan 默认化应进入 diff");
                    assertFalse(cell.smokeOnly());
                } else {
                    assertFalse(cell.diffEnabled(), "26.4 之前 VulkanMod 只做启动 + 渲染一帧冒烟");
                    assertTrue(cell.smokeOnly());
                }
            } else {
                assertTrue(cell.diffEnabled());
                assertFalse(cell.smokeOnly());
            }
        }
        assertFalse(new RenderMatrixCell("26.3", RenderBackend.VULKANMOD, null, true, 0.5).diffEnabled(),
                "构造器必须把 26.3 VulkanMod 的 diff 归一化为 false");
        assertTrue(new RenderMatrixCell("26.4", RenderBackend.VULKANMOD, null, true, 0.8).diffEnabled(),
                "26.4 VulkanMod 允许 diff");
    }

    @Test
    void sodiumCellCarriesBundledLowLoadShader() {
        RenderMatrixCell sodium = RenderMatrix.defaults().cells().stream()
                .filter(cell -> cell.backend() == RenderBackend.SODIUM)
                .findFirst()
                .orElseThrow();

        assertEquals(ShaderPackSpec.LOW_LOAD.name(), sodium.shaderPack());
        assertTrue(ShaderPackSpec.LOW_LOAD.lightweight());
    }

    @Test
    void goldenPathUsesMinecraftVersionAndBackendDirectories() {
        RenderMatrixCell vulkan = new RenderMatrixCell("26.4", RenderBackend.VULKANMOD, null, true, 0.8);
        GoldenPathResolver resolver = new GoldenPathResolver(Path.of("golden"));

        String directory = resolver.directory(vulkan).toString().replace('\\', '/');
        String png = resolver.goldenPng(vulkan, "world-spawn").toString().replace('\\', '/');

        assertTrue(directory.contains("26.4"), directory);
        assertTrue(directory.contains("vulkanmod"), directory);
        assertTrue(png.endsWith("26.4/vulkanmod/world-spawn.png"), png);
    }

    @Test
    void matrixKeepsMinecraftOuterAndBackendInnerOrder() {
        List<RenderMatrixCell> cells = RenderMatrix.defaults().cells();

        assertEquals("26.4", cells.get(0).mcVersion());
        assertEquals(RenderBackend.VANILLA_OPENGL, cells.get(0).backend());
        assertEquals(RenderBackend.VULKANMOD, cells.get(2).backend());
        assertEquals("26.3", cells.get(3).mcVersion());
        assertEquals("26.2", cells.get(6).mcVersion());
        assertEquals("26.1", cells.get(9).mcVersion());
        assertEquals("1.21.1", cells.get(12).mcVersion());
    }

    @Test
    void toleranceIsTieredByPipeline() {
        List<RenderMatrixCell> cells = RenderMatrix.defaults().cells();

        assertTrue(cells.stream()
                .filter(cell -> cell.mcVersion().startsWith("26."))
                .filter(cell -> cell.backend() != RenderBackend.VULKANMOD)
                .allMatch(cell -> cell.tolerancePercent() == 0.5));
        assertTrue(cells.stream()
                .filter(cell -> cell.mcVersion().startsWith("26."))
                .filter(cell -> cell.backend() == RenderBackend.VULKANMOD)
                .allMatch(cell -> cell.tolerancePercent() == 0.8));
        assertTrue(cells.stream().filter(cell -> !cell.mcVersion().startsWith("26."))
                .allMatch(cell -> cell.tolerancePercent() == 1.0));

        assertEquals(RenderPipeline.OPENGL, RenderBackend.VANILLA_OPENGL.pipeline());
        assertEquals(RenderPipeline.VULKAN, RenderBackend.VULKANMOD.pipeline());
    }
}
