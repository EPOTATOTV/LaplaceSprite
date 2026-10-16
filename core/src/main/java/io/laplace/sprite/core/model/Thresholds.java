package io.laplace.sprite.core.model;

/**
 * 门禁阈值配置（4.5 判定层）。
 *
 * @param maxFalsePositiveRate 合法误报率上限（默认 0.01%）
 * @param minDetectionRate     作弊检出率下限（默认 95%）
 * @param minCoverage          核心模块行覆盖率下限（默认 70%）
 * @param requireConcurrency   是否要求并发安全全通过
 * @param requireBoundary      是否要求阈值边界样本全部符合预期
 */
public record Thresholds(
        double maxFalsePositiveRate,
        double minDetectionRate,
        double minCoverage,
        boolean requireConcurrency,
        boolean requireBoundary) {

    public static Thresholds defaults() {
        return new Thresholds(0.0001, 0.95, 0.70, true, true);
    }
}
