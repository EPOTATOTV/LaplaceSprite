package io.laplace.sprite.render;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * PNG 像素 diff（15.12 P0）。
 *
 * <p>判定口径：逐像素逐通道比较，差值超过 {@code perChannelTolerance} 记为差异像素；
 * 差异率 = 差异像素数 / 总像素数 × 100，小于等于 {@code tolerancePercent} 才通过。
 * 尺寸不一致不做缩放（缩放会把布局错误洗成低分差异），直接判失败。</p>
 */
public final class PixelDiff {

    private static final int DIFF_COLOR = 0xFFFF0000;

    private PixelDiff() {
    }

    /** 比较两张同角度截图。 */
    public static PixelDiffResult compare(Screenshot golden, Screenshot actual, PixelDiffOptions options) {
        Objects.requireNonNull(golden, "golden");
        Objects.requireNonNull(actual, "actual");
        Objects.requireNonNull(options, "options");
        RgbMean goldenMean = mean(golden);
        RgbMean actualMean = mean(actual);
        if (golden.width() != actual.width() || golden.height() != actual.height()) {
            int total = golden.pixelCount();
            return new PixelDiffResult(false, true, total, total, 100.0,
                    Optional.of(new BoundingBox(0, 0, actual.width() - 1, actual.height() - 1)),
                    goldenMean, actualMean,
                    "尺寸不一致（golden %dx%d，actual %dx%d）：不做缩放，直接判失败"
                            .formatted(golden.width(), golden.height(), actual.width(), actual.height()));
        }

        int width = golden.width();
        int height = golden.height();
        int differing = 0;
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!differs(golden.pixelAt(x, y), actual.pixelAt(x, y), options)) {
                    continue;
                }
                differing++;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        int total = golden.pixelCount();
        double diffPercent = (double) differing * 100.0 / total;
        boolean passed = diffPercent <= options.tolerancePercent();
        Optional<BoundingBox> box = differing == 0
                ? Optional.empty()
                : Optional.of(new BoundingBox(minX, minY, maxX, maxY));
        String reason = String.format(Locale.ROOT, "差异 %d/%d 像素（%.2f%%），容差 %.2f%%：%s",
                differing, total, diffPercent, options.tolerancePercent(), passed ? "通过" : "超过容差");
        return new PixelDiffResult(passed, false, differing, total, diffPercent, box, goldenMean, actualMean,
                reason);
    }

    /** 写出差异图：以实际截图为底，差异像素涂红。 */
    public static Path writeDiffPng(Screenshot golden, Screenshot actual, PixelDiffOptions options, Path target) {
        Objects.requireNonNull(golden, "golden");
        Objects.requireNonNull(actual, "actual");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(target, "target");
        BufferedImage image = new BufferedImage(actual.width(), actual.height(), BufferedImage.TYPE_INT_ARGB);
        boolean sameSize = golden.width() == actual.width() && golden.height() == actual.height();
        for (int y = 0; y < actual.height(); y++) {
            for (int x = 0; x < actual.width(); x++) {
                int pixel = actual.pixelAt(x, y);
                // 尺寸不一致时坐标没有对齐关系，只做副本，不涂红。
                boolean highlight = sameSize && differs(golden.pixelAt(x, y), pixel, options);
                image.setRGB(x, y, highlight ? DIFF_COLOR : pixel);
            }
        }
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            if (!ImageIO.write(image, "png", target.toFile())) {
                throw new IllegalStateException("没有可用的 PNG 编码器: " + target);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("写入 diff PNG 失败: " + target, ex);
        }
        return target;
    }

    /** 一次产出判定结果与差异图，路径原样带进证据记录。 */
    public static DiffArtifact diffToFile(Screenshot golden, Screenshot actual, Path goldenPath, Path actualPath,
                                          Path diffPath, PixelDiffOptions options) {
        PixelDiffResult result = compare(golden, actual, options);
        Path written = writeDiffPng(golden, actual, options, diffPath);
        return new DiffArtifact(goldenPath, actualPath, written, result.diffPercent(), result.passed());
    }

    private static boolean differs(int golden, int actual, PixelDiffOptions options) {
        int tolerance = options.perChannelTolerance();
        if (Math.abs(((golden >> 16) & 0xFF) - ((actual >> 16) & 0xFF)) > tolerance) {
            return true;
        }
        if (Math.abs(((golden >> 8) & 0xFF) - ((actual >> 8) & 0xFF)) > tolerance) {
            return true;
        }
        if (Math.abs((golden & 0xFF) - (actual & 0xFF)) > tolerance) {
            return true;
        }
        return !options.ignoreAlpha() && Math.abs((golden >>> 24) - (actual >>> 24)) > tolerance;
    }

    private static RgbMean mean(Screenshot screenshot) {
        long r = 0;
        long g = 0;
        long b = 0;
        for (int pixel : screenshot.pixels()) {
            r += (pixel >> 16) & 0xFF;
            g += (pixel >> 8) & 0xFF;
            b += pixel & 0xFF;
        }
        int count = screenshot.pixelCount();
        return new RgbMean((double) r / count, (double) g / count, (double) b / count);
    }
}
