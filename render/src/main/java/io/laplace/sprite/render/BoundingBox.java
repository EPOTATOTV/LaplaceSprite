package io.laplace.sprite.render;

/**
 * 差异像素包围盒（15.12 P0）。
 *
 * @param minX 最左差异像素 x
 * @param minY 最上差异像素 y
 * @param maxX 最右差异像素 x
 * @param maxY 最下差异像素 y
 */
public record BoundingBox(int minX, int minY, int maxX, int maxY) {

    public BoundingBox {
        if (minX < 0 || minY < 0 || maxX < minX || maxY < minY) {
            throw new IllegalArgumentException(
                    "差异包围盒非法: (%d,%d)-(%d,%d)".formatted(minX, minY, maxX, maxY));
        }
    }

    public int width() {
        return maxX - minX + 1;
    }

    public int height() {
        return maxY - minY + 1;
    }
}
