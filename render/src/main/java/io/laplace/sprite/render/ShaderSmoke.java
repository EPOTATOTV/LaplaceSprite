package io.laplace.sprite.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 光影 shader 冒烟（15.12 P2）。
 *
 * <p>只做「不崩 + 出图」，不做像素门禁。三条断言：
 * 日志里没有 shader/compile 错误行；截图不是全黑也不是全白；加载后世界仍可交互（探针由调用方注入）。</p>
 */
public final class ShaderSmoke {

    /** 全黑判定：平均亮度不高于该值且亮度跨度也很小。 */
    public static final double BLACK_MEAN_MAX = 2.0;

    /** 全白判定：平均亮度不低于该值且亮度跨度也很小。 */
    public static final double WHITE_MEAN_MIN = 253.0;

    /** 纯色/全黑/全白的亮度跨度上限；真实场景总有明暗差，跨度会明显更大。 */
    public static final double FLAT_SPREAD_MAX = 2.0;

    private static final List<String> ERROR_WORDS = List.of("error", "failed", "exception", "invalid", "cannot");

    /** 交互探针：真实运行由客户端脚本注入，离线测试传固定值。 */
    @FunctionalInterface
    public interface InteractionProbe {
        boolean stillInteractive();
    }

    private final InteractionProbe interactionProbe;

    public ShaderSmoke(InteractionProbe interactionProbe) {
        this.interactionProbe = Objects.requireNonNull(interactionProbe, "interactionProbe");
    }

    public ShaderSmokeResult check(String launchLog, Screenshot screenshot) {
        Objects.requireNonNull(screenshot, "screenshot");
        List<String> failures = new ArrayList<>();
        String errorLine = firstShaderError(launchLog);
        if (errorLine != null) {
            failures.add("shader 编译/加载错误：" + errorLine);
        }

        double meanLuminance = 0;
        double spread = 0;
        double r = 0;
        double g = 0;
        double b = 0;
        int count = screenshot.pixelCount();
        double min = 255;
        double max = 0;
        for (int pixel : screenshot.pixels()) {
            int red = (pixel >> 16) & 0xFF;
            int green = (pixel >> 8) & 0xFF;
            int blue = pixel & 0xFF;
            r += red;
            g += green;
            b += blue;
            double luminance = 0.2126 * red + 0.7152 * green + 0.0722 * blue;
            meanLuminance += luminance;
            min = Math.min(min, luminance);
            max = Math.max(max, luminance);
        }
        meanLuminance /= count;
        r /= count;
        g /= count;
        b /= count;
        spread = max - min;

        if (meanLuminance <= BLACK_MEAN_MAX && spread <= FLAT_SPREAD_MAX) {
            failures.add(String.format(Locale.ROOT, "画面全黑：平均亮度 %.2f，亮度跨度 %.2f", meanLuminance, spread));
        }
        if (meanLuminance >= WHITE_MEAN_MIN && spread <= FLAT_SPREAD_MAX) {
            failures.add(String.format(Locale.ROOT, "画面全白：平均亮度 %.2f，亮度跨度 %.2f", meanLuminance, spread));
        }
        if (!interactionProbe.stillInteractive()) {
            failures.add("世界不可交互：加载光影后交互探测失败");
        }
        return new ShaderSmokeResult(failures.isEmpty(), failures, meanLuminance, spread,
                new RgbMean(r, g, b));
    }

    /** 找第一条同时出现 shader/compile 与错误词的行。 */
    private static String firstShaderError(String launchLog) {
        if (launchLog == null || launchLog.isBlank()) {
            return null;
        }
        for (String line : launchLog.split("\\R")) {
            String lower = line.toLowerCase(Locale.ROOT);
            boolean shaderContext = lower.contains("shader") || lower.contains("compile");
            if (shaderContext && ERROR_WORDS.stream().anyMatch(lower::contains)) {
                return line.trim();
            }
        }
        return null;
    }
}
