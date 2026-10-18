package io.laplace.sprite.render;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 渲染套件执行器（L-06）：golden / actual 目录比对与缺失处理。 */
class RenderSuiteRunnerTest {

    @TempDir
    Path tempDir;

    private static final RenderGoldenLibrary.GoldenCase CASE =
            new RenderGoldenLibrary.GoldenCase("sulfur-cavern", "26.2", "硫磺洞穴");

    @Test
    void identicalScreenshotsPassForDiffEnabledBackends() throws IOException {
        Path goldenRoot = tempDir.resolve("golden");
        Path actualRoot = tempDir.resolve("actual");
        writePng(goldenRoot, RenderBackend.VANILLA_OPENGL, 0xFFFFFFFF);
        writePng(actualRoot, RenderBackend.VANILLA_OPENGL, 0xFFFFFFFF);
        writePng(goldenRoot, RenderBackend.SODIUM, 0xFF3366AA);
        writePng(actualRoot, RenderBackend.SODIUM, 0xFF3366AA);

        RenderSuiteRunner.Report report = new RenderSuiteRunner()
                .run(goldenRoot, actualRoot, List.of(CASE));

        assertEquals(2, report.cases().size(), "VulkanMod 不参与 diff");
        assertTrue(report.passed(), report.toMarkdown());
        assertTrue(report.cases().stream().allMatch(c -> c.status() == RenderSuiteRunner.Status.PASSED));
    }

    @Test
    void differentPixelsFailWithDiffPercentage() throws IOException {
        Path goldenRoot = tempDir.resolve("golden");
        Path actualRoot = tempDir.resolve("actual");
        writePng(goldenRoot, RenderBackend.VANILLA_OPENGL, 0xFFFFFFFF);
        writePng(actualRoot, RenderBackend.VANILLA_OPENGL, 0xFF000000);

        RenderSuiteRunner.Report report = new RenderSuiteRunner()
                .run(goldenRoot, actualRoot, List.of(CASE));

        assertFalse(report.passed());
        assertEquals(1, report.count(RenderSuiteRunner.Status.FAILED));
        assertEquals(100.0, report.cases().get(0).diffPercent(), 1e-9);
    }

    @Test
    void missingActualIsReportedAsMissingNotFailed() throws IOException {
        Path goldenRoot = tempDir.resolve("golden");
        Path actualRoot = tempDir.resolve("actual");
        writePng(goldenRoot, RenderBackend.VANILLA_OPENGL, 0xFF00FF00);

        RenderSuiteRunner.Report report = new RenderSuiteRunner()
                .run(goldenRoot, actualRoot, List.of(CASE));

        assertFalse(report.passed());
        assertEquals(2, report.count(RenderSuiteRunner.Status.MISSING));
        assertTrue(report.cases().get(0).reason().contains("缺少实拍截图"));
    }

    private void writePng(Path root, RenderBackend backend, int argb) throws IOException {
        RenderMatrixCell cell = new RenderMatrixCell("26.2", backend, null, true, 0.5);
        Path file = new GoldenPathResolver(root).goldenPng(cell, CASE.id());
        Files.createDirectories(file.getParent());
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                image.setRGB(x, y, argb);
            }
        }
        ImageIO.write(image, "png", file.toFile());
    }
}
