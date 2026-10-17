package io.laplace.sprite.render;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * 客户端截图像素表（15.12 P0）。
 *
 * <p>只接受 PNG：像素 diff 的基准图来自 {@code takeScreenshot}，格式漂移本身就是问题，
 * 所以非 PNG 直接报中文错误，不做格式猜测。</p>
 *
 * @param width  宽
 * @param height 高
 * @param pixels ARGB 像素，行优先
 */
public record Screenshot(int width, int height, int[] pixels) {

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G', 0x0D, 0x0A, 0x1A, 0x0A
    };

    public Screenshot {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("截图尺寸必须为正: " + width + "x" + height);
        }
        if (pixels == null || pixels.length != width * height) {
            throw new IllegalArgumentException(
                    "像素数组长度与尺寸不符: 期望 %d，实际 %s".formatted(width * height,
                            pixels == null ? "null" : pixels.length));
        }
        pixels = Arrays.copyOf(pixels, pixels.length);
    }

    /** 从 PNG 字节解码；签名不对或无法解码时抛 {@link IllegalArgumentException}。 */
    public static Screenshot fromPng(byte[] png) {
        if (png == null || png.length < PNG_SIGNATURE.length) {
            throw new IllegalArgumentException("截图不是 PNG：字节为空或长度不足");
        }
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (png[i] != PNG_SIGNATURE[i]) {
                throw new IllegalArgumentException("截图不是 PNG：文件头签名不匹配");
            }
        }
        BufferedImage image;
        try {
            image = ImageIO.read(new ByteArrayInputStream(png));
        } catch (IOException ex) {
            throw new IllegalArgumentException("PNG 解码失败：" + ex.getMessage(), ex);
        }
        if (image == null) {
            throw new IllegalArgumentException("PNG 解码失败：文件已损坏或使用了不支持的编码");
        }
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        return new Screenshot(width, height, pixels);
    }

    /** 从磁盘读 PNG。 */
    public static Screenshot fromPath(Path path) {
        try {
            return fromPng(Files.readAllBytes(path));
        } catch (IOException ex) {
            throw new UncheckedIOException("读取截图失败: " + path, ex);
        }
    }

    public int pixelAt(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throw new IndexOutOfBoundsException("像素坐标越界: (%d,%d) 尺寸 %dx%d".formatted(x, y, width, height));
        }
        return pixels[y * width + x];
    }

    public int pixelCount() {
        return width * height;
    }
}
