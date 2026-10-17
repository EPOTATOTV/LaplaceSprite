package io.laplace.sprite.render;

/**
 * 像素 diff 选项（15.12 P0）。
 *
 * <p>默认 0.5% 像素容差来自文档：同角度截图受 GPU / 驱动 / 抗锯齿影响会有少量像素抖动，
 * 全量逐像素相等不现实；0.5% 是门禁默认值，渲染后端组合矩阵另可放宽。</p>
 *
 * @param tolerancePercent   允许的差异像素百分比（0–100）
 * @param ignoreAlpha        是否忽略 alpha 通道
 * @param perChannelTolerance 单通道差值不超过该值视为同色（0–255）
 */
public record PixelDiffOptions(double tolerancePercent, boolean ignoreAlpha, int perChannelTolerance) {

    public static final double DEFAULT_TOLERANCE_PERCENT = 0.5;

    public PixelDiffOptions {
        if (tolerancePercent < 0 || tolerancePercent > 100) {
            throw new IllegalArgumentException("像素容差必须在 0–100 之间: " + tolerancePercent);
        }
        if (perChannelTolerance < 0 || perChannelTolerance > 255) {
            throw new IllegalArgumentException("单通道容差必须在 0–255 之间: " + perChannelTolerance);
        }
    }

    /** 文档默认档：0.5% 像素容差、不忽略 alpha、逐通道零容差。 */
    public static PixelDiffOptions defaults() {
        return new PixelDiffOptions(DEFAULT_TOLERANCE_PERCENT, false, 0);
    }
}
