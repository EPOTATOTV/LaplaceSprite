package io.laplace.sprite.render;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 渲染后端矩阵（15.12 P1）：VulkanMod 冒烟免 diff、golden 按 MC × 后端分目录。 */
class RenderMatrixTest {

    @Test
    void defaultsCoverThreeBackendsForEachMinecraftVersion() {
        RenderMatrix matrix = RenderMatrix.defaults();

        assertEquals(9, matrix.size());
        assertEquals(9, matrix.cells().size());
        assertEquals(3, matrix.mcVersions().size());
        assertEquals(3, matrix.backends().size());
    }

    @Test
    void vulkanModCellsNeverEnablePixelDiff() {
        for (RenderMatrixCell cell : RenderMatrix.defaults().cells()) {
            if (cell.backend() == RenderBackend.VULKANMOD) {
                assertFalse(cell.diffEnabled(), "VulkanMod 只做启动 + 渲染一帧冒烟");
                assertTrue(cell.smokeOnly());
            } else {
                assertTrue(cell.diffEnabled());
                assertFalse(cell.smokeOnly());
            }
        }
        RenderMatrixCell forced = new RenderMatrixCell("1.21.1", RenderBackend.VULKANMOD, null, true, 0.5);
        assertFalse(forced.diffEnabled(), "构造器必须把 VulkanMod 的 diff 归一化为 false");
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
        RenderMatrixCell vulkan = new RenderMatrixCell("1.21.1", RenderBackend.VULKANMOD, null, true, 0.5);
        GoldenPathResolver resolver = new GoldenPathResolver(Path.of("golden"));

        String directory = resolver.directory(vulkan).toString().replace('\\', '/');
        String png = resolver.goldenPng(vulkan, "world-spawn").toString().replace('\\', '/');

        assertTrue(directory.contains("1.21.1"), directory);
        assertTrue(directory.contains("vulkanmod"), directory);
        assertTrue(png.endsWith("1.21.1/vulkanmod/world-spawn.png"), png);
    }

    @Test
    void matrixKeepsMinecraftOuterAndBackendInnerOrder() {
        List<RenderMatrixCell> cells = RenderMatrix.defaults().cells();

        assertEquals("26.2", cells.get(0).mcVersion());
        assertEquals(RenderBackend.VANILLA_OPENGL, cells.get(0).backend());
        assertEquals(RenderBackend.VULKANMOD, cells.get(2).backend());
        assertEquals("26.1", cells.get(3).mcVersion());
        assertEquals("1.21.1", cells.get(6).mcVersion());
    }

    @Test
    void twentySixVersionsUseTheTighterTolerance() {
        List<RenderMatrixCell> cells = RenderMatrix.defaults().cells();

        assertTrue(cells.stream().filter(cell -> cell.mcVersion().startsWith("26."))
                .allMatch(cell -> cell.tolerancePercent() == 0.5));
        assertTrue(cells.stream().filter(cell -> !cell.mcVersion().startsWith("26."))
                .allMatch(cell -> cell.tolerancePercent() == 1.0));
    }
}
