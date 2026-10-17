package io.laplace.sprite.render;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 像素 diff（15.12 P0）：0.5% 容差、尺寸不一致判失败、差异图标红。 */
class PixelDiffTest {

    private static final int BASE = 0xFF102030;

    @Test
    void identicalImagesPass() {
        Screenshot image = solid(20, 10, BASE);

        PixelDiffResult result = PixelDiff.compare(image, image, PixelDiffOptions.defaults());

        assertTrue(result.passed());
        assertEquals(0, result.differingPixels());
        assertEquals(0.0, result.diffPercent(), 1e-9);
        assertTrue(result.boundingBox().isEmpty());
        assertEquals(0x10, result.goldenMean().r(), 1e-9);
        assertEquals(BASE & 0xFF, result.actualMean().b(), 1e-9);
    }

    @Test
    void onePixelOutOfTwoHundredIsExactlyHalfPercentAndPasses() {
        Screenshot golden = solid(20, 10, BASE);
        Screenshot actual = withDifferences(20, 10, BASE, 1);

        PixelDiffResult result = PixelDiff.compare(golden, actual, PixelDiffOptions.defaults());

        assertEquals(1, result.differingPixels());
        assertEquals(200, result.totalPixels());
        assertEquals(0.5, result.diffPercent(), 1e-9);
        assertTrue(result.passed(), "0.5% 恰好等于默认容差，应通过");
        assertEquals(new BoundingBox(0, 0, 0, 0), result.boundingBox().orElseThrow());
    }

    @Test
    void twoPixelsOutOfTwoHundredFail() {
        Screenshot golden = solid(20, 10, BASE);
        Screenshot actual = withDifferences(20, 10, BASE, 2);

        PixelDiffResult result = PixelDiff.compare(golden, actual, PixelDiffOptions.defaults());

        assertEquals(2, result.differingPixels());
        assertEquals(1.0, result.diffPercent(), 1e-9);
        assertFalse(result.passed(), "1% 超过 0.5% 容差");
        assertTrue(result.reason().contains("超过容差"), result.reason());
        assertEquals(new BoundingBox(0, 0, 1, 0), result.boundingBox().orElseThrow());
    }

    @Test
    void sizeMismatchFailsWithoutResize() {
        Screenshot golden = solid(20, 10, BASE);
        Screenshot actual = solid(20, 11, BASE);

        PixelDiffResult result = PixelDiff.compare(golden, actual, PixelDiffOptions.defaults());

        assertFalse(result.passed());
        assertTrue(result.sizeMismatch());
        assertEquals(100.0, result.diffPercent(), 1e-9);
        assertTrue(result.reason().contains("尺寸不一致"), result.reason());
    }

    @Test
    void perChannelToleranceAbsorbsSmallNoise() {
        Screenshot golden = solid(4, 4, 0xFF404040);
        Screenshot actual = solid(4, 4, 0xFF454545);

        PixelDiffResult result = PixelDiff.compare(golden, actual,
                new PixelDiffOptions(0.5, false, 5));

        assertTrue(result.passed());
        assertEquals(0, result.differingPixels());
    }

    @Test
    void diffPngHighlightsDifferingPixelInRed(@TempDir Path dir) throws IOException {
        Screenshot golden = solid(8, 8, BASE);
        int[] pixels = new int[64];
        Arrays.fill(pixels, BASE);
        pixels[4 * 8 + 3] = 0xFFFFFFFF;
        Screenshot actual = new Screenshot(8, 8, pixels);

        Path diffPath = dir.resolve("artifacts/diff.png");
        DiffArtifact artifact = PixelDiff.diffToFile(golden, actual, dir.resolve("golden.png"),
                dir.resolve("actual.png"), diffPath, PixelDiffOptions.defaults());

        assertFalse(artifact.passed());
        assertEquals(100.0 / 64.0, artifact.diffPercent(), 1e-9);
        assertTrue(Files.isRegularFile(diffPath));
        Screenshot diff = Screenshot.fromPng(Files.readAllBytes(diffPath));
        assertEquals(0xFFFF0000, diff.pixelAt(3, 4), "差异像素必须标红");
        assertEquals(BASE, diff.pixelAt(0, 0), "相同像素保留实际截图颜色");
    }

    private static Screenshot solid(int width, int height, int argb) {
        int[] pixels = new int[width * height];
        Arrays.fill(pixels, argb);
        return new Screenshot(width, height, pixels);
    }

    private static Screenshot withDifferences(int width, int height, int base, int count) {
        int[] pixels = new int[width * height];
        Arrays.fill(pixels, base);
        for (int i = 0; i < count; i++) {
            pixels[i] = 0xFFFFFFFF;
        }
        return new Screenshot(width, height, pixels);
    }
}
