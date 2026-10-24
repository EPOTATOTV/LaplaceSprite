package io.laplace.sprite.render;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 渲染金标准样本库（L-06 / L2-02）：26.2 内容 + 26.3 基线 + 26.4 Vulkan 管线。 */
class RenderGoldenLibraryTest {

    private final RenderGoldenLibrary library = new RenderGoldenLibrary();

    @Test
    void coversTwentySixTwoNewContent() {
        List<RenderGoldenLibrary.GoldenCase> cases = library.forVersion("26.2");

        assertEquals(3, cases.size());
        assertEquals(List.of("sulfur-cavern", "new-dimension", "new-effects"),
                cases.stream().map(RenderGoldenLibrary.GoldenCase::id).toList());
        assertTrue(cases.stream().allMatch(golden -> !golden.description().isBlank()));
    }

    @Test
    void coversTwentySixThreeBaselineAndTwentySixFourVulkanPipeline() {
        assertEquals(List.of("world-spawn"), library.forVersion("26.3").stream()
                .map(RenderGoldenLibrary.GoldenCase::id).toList());
        assertEquals(List.of("oit-depth-bins", "vulkan-terrain"), library.forVersion("26.4").stream()
                .map(RenderGoldenLibrary.GoldenCase::id).toList());
    }

    @Test
    void goldenPathUsesVersionAndBackendDirectories() {
        RenderGoldenLibrary.GoldenCase sulfur = library.forVersion("26.2").get(0);
        Path path = library.goldenPng(new GoldenPathResolver(Path.of("golden")), sulfur,
                RenderBackend.VANILLA_OPENGL);

        String normalized = path.toString().replace('\\', '/');
        assertTrue(normalized.endsWith("golden/26.2/vanilla-opengl/sulfur-cavern.png"), normalized);
    }

    @Test
    void smokeOnlyBackendIsRejectedBeforeVulkanDefault() {
        RenderGoldenLibrary.GoldenCase sulfur = library.forVersion("26.2").get(0);

        assertThrows(IllegalArgumentException.class,
                () -> library.goldenPng(new GoldenPathResolver(Path.of("golden")), sulfur,
                        RenderBackend.VULKANMOD));
    }

    @Test
    void vulkanGoldenIsAvailableFromTwentySixFour() {
        RenderGoldenLibrary.GoldenCase oit = library.forVersion("26.4").get(0);
        Path path = library.goldenPng(new GoldenPathResolver(Path.of("golden")), oit,
                RenderBackend.VULKANMOD);

        String normalized = path.toString().replace('\\', '/');
        assertTrue(normalized.endsWith("golden/26.4/vulkanmod/oit-depth-bins.png"), normalized);
        assertEquals(0.8, RenderMatrix.toleranceFor("26.4", RenderBackend.VULKANMOD));
    }
}
