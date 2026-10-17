package io.laplace.sprite.render;

import java.util.List;

/**
 * 光影冒烟结果（15.12 P2）。
 *
 * @param passed          是否通过
 * @param failures        中文失败原因
 * @param meanLuminance   全图平均亮度
 * @param luminanceSpread 亮度跨度（最亮像素 − 最暗像素）
 * @param meanRgb         平均 RGB
 */
public record ShaderSmokeResult(boolean passed, List<String> failures, double meanLuminance,
                                double luminanceSpread, RgbMean meanRgb) {

    public ShaderSmokeResult {
        failures = failures == null ? List.of() : List.copyOf(failures);
    }
}
