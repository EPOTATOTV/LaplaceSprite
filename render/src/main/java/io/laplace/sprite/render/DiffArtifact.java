package io.laplace.sprite.render;

import java.nio.file.Path;

/**
 * 一次像素 diff 的证据产物（15.12 P0）。
 *
 * @param goldenPath golden 模板路径
 * @param actualPath 实际截图路径
 * @param diffPath   差异图路径（实际截图的副本，差异像素标红）
 * @param diffPercent 差异像素百分比
 * @param passed     是否通过
 */
public record DiffArtifact(Path goldenPath, Path actualPath, Path diffPath, double diffPercent, boolean passed) {
}
