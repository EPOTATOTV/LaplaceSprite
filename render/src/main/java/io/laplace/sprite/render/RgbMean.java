package io.laplace.sprite.render;

/**
 * 平均 RGB。
 *
 * @param r 红色均值（0–255）
 * @param g 绿色均值（0–255）
 * @param b 蓝色均值（0–255）
 */
public record RgbMean(double r, double g, double b) {

    /** 三通道算术平均，用于全黑/全白判定。 */
    public double average() {
        return (r + g + b) / 3.0;
    }
}
