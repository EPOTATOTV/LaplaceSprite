package io.laplace.sprite.render;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 截图解码（15.12 P0）：只收 PNG。 */
class ScreenshotTest {

    @Test
    void decodesPngBytesAndPreservesPixels() throws IOException {
        byte[] png = png(2, 2, new int[]{0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFFFF});

        Screenshot screenshot = Screenshot.fromPng(png);

        assertEquals(2, screenshot.width());
        assertEquals(2, screenshot.height());
        assertEquals(0xFFFF0000, screenshot.pixelAt(0, 0));
        assertEquals(0xFF00FF00, screenshot.pixelAt(1, 0));
        assertEquals(0xFFFFFFFF, screenshot.pixelAt(1, 1));
    }

    @Test
    void rejectsNonPngBytesWithChineseReason() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> Screenshot.fromPng("fixture-not-a-real-screenshot".getBytes()));

        assertTrue(error.getMessage().contains("不是 PNG"), error.getMessage());
    }

    @Test
    void rejectsCorruptedPngAfterValidSignature() {
        byte[] corrupted = {(byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G', 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x00};

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> Screenshot.fromPng(corrupted));

        assertTrue(error.getMessage().contains("解码失败"), error.getMessage());
    }

    @Test
    void rejectsPixelArrayWithWrongLength() {
        assertThrows(IllegalArgumentException.class, () -> new Screenshot(2, 2, new int[3]));
    }

    static byte[] png(int width, int height, int[] pixels) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, pixels[y * width + x]);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
