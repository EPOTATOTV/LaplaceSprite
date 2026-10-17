package io.laplace.sprite.core.model;

/**
 * 门禁阈值配置（4.5 判定层）。
 *
 * @param maxFalsePositiveRate       合法误报率上限（默认 0.01%）
 * @param minDetectionRate           作弊检出率下限（默认 95%）
 * @param minCoverage                核心模块行覆盖率下限（默认 70%）
 * @param requireConcurrency         是否要求并发安全全通过
 * @param requireBoundary            是否要求阈值边界样本全部符合预期
 * @param minFunctionalSmokePassRate 功能冒烟通过率下限（默认 100%）
 * @param requirePerformanceBaseline 是否要求性能不劣化于上一版本基线
 * @param requireResourceAssembly    是否要求资源与装配全部通过
 */
public record Thresholds(
        double maxFalsePositiveRate,
        double minDetectionRate,
        double minCoverage,
        boolean requireConcurrency,
        boolean requireBoundary,
        double minFunctionalSmokePassRate,
        boolean requirePerformanceBaseline,
        boolean requireResourceAssembly) {

    public static Thresholds defaults() {
        return new Thresholds(0.0001, 0.95, 0.70, true, true, 1.0, true, true);
    }

    /** 兼容构造：新增三项按 4.5 默认门禁补齐（冒烟 100%、性能与资源装配均要求通过）。 */
    public Thresholds(double maxFalsePositiveRate, double minDetectionRate, double minCoverage,
                      boolean requireConcurrency, boolean requireBoundary) {
        this(maxFalsePositiveRate, minDetectionRate, minCoverage, requireConcurrency, requireBoundary,
                1.0, true, true);
    }
}
