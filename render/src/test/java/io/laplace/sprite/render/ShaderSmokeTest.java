package io.laplace.sprite.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 光影冒烟（15.12 P2）：不崩 + 出图，不看像素门禁。 */
class ShaderSmokeTest {

    @Test
    void allBlackScreenshotFails() {
        ShaderSmokeResult result = smoke().check("", solid(16, 16, 0xFF000000));

        assertFalse(result.passed());
        assertTrue(result.failures().stream().anyMatch(failure -> failure.contains("全黑")),
                String.join("；", result.failures()));
    }

    @Test
    void allWhiteScreenshotFails() {
        ShaderSmokeResult result = smoke().check("", solid(16, 16, 0xFFFFFFFF));

        assertFalse(result.passed());
        assertTrue(result.failures().stream().anyMatch(failure -> failure.contains("全白")),
                String.join("；", result.failures()));
    }

    @Test
    void normalGradientPasses() {
        ShaderSmokeResult result = smoke().check("[main/INFO] Shaders loaded\n", gradient());

        assertTrue(result.passed(), String.join("；", result.failures()));
        assertTrue(result.meanLuminance() > ShaderSmoke.BLACK_MEAN_MAX);
        assertTrue(result.meanLuminance() < ShaderSmoke.WHITE_MEAN_MIN);
        assertTrue(result.luminanceSpread() > ShaderSmoke.FLAT_SPREAD_MAX);
    }

    @Test
    void shaderCompileErrorLineFails() {
        ShaderSmokeResult result = smoke().check(
                "[Render thread/ERROR] Shader compile failed: laplace-lowload\n", gradient());

        assertFalse(result.passed());
        assertTrue(result.failures().stream().anyMatch(failure -> failure.contains("shader 编译")),
                String.join("；", result.failures()));
    }

    @Test
    void unrelatedErrorLineDoesNotFail() {
        ShaderSmokeResult result = smoke().check("[main/ERROR] Failed to load texture", gradient());

        assertTrue(result.passed(), String.join("；", result.failures()));
    }

    @Test
    void nonInteractiveWorldFails() {
        ShaderSmokeResult result = new ShaderSmoke(() -> false).check("", gradient());

        assertFalse(result.passed());
        assertTrue(result.failures().stream().anyMatch(failure -> failure.contains("不可交互")),
                String.join("；", result.failures()));
    }

    private static ShaderSmoke smoke() {
        return new ShaderSmoke(() -> true);
    }

    private static Screenshot solid(int width, int height, int argb) {
        int[] pixels = new int[width * height];
        java.util.Arrays.fill(pixels, argb);
        return new Screenshot(width, height, pixels);
    }

    private static Screenshot gradient() {
        int width = 16;
        int height = 16;
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int red = x * 17;
                int green = y * 17;
                int blue = (x + y) * 8;
                pixels[y * width + x] = 0xFF000000 | (red << 16) | (green << 8) | blue;
            }
        }
        return new Screenshot(width, height, pixels);
    }
}
