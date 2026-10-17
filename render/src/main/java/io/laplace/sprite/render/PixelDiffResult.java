package io.laplace.sprite.render;

import java.util.Optional;

/**
 * 像素 diff 结果（15.12 P0）。
 *
 * @param passed         是否通过门禁
 * @param sizeMismatch   尺寸是否不一致；不一致按失败处理
 * @param differingPixels 差异像素数
 * @param totalPixels    参与比较的像素总数
 * @param diffPercent    差异像素百分比
 * @param boundingBox    差异包围盒；无差异时为空
 * @param goldenMean     golden 的平均 RGB
 * @param actualMean     actual 的平均 RGB
 * @param reason         中文判定说明
 */
public record PixelDiffResult(boolean passed, boolean sizeMismatch, int differingPixels, int totalPixels,
                              double diffPercent, Optional<BoundingBox> boundingBox, RgbMean goldenMean,
                              RgbMean actualMean, String reason) {

    public PixelDiffResult {
        boundingBox = boundingBox == null ? Optional.empty() : boundingBox;
    }
}
