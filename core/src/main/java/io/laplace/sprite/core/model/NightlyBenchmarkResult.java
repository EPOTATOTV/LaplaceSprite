package io.laplace.sprite.core.model;

import java.time.Instant;

/**
 * 夜间基准结果（11.2 夜间基准 / 12.4 基线）。
 *
 * @param night           基准日期
 * @param passed          是否通过
 * @param durationMs      耗时
 * @param totalRuns       运行总数
 * @param failedRuns      失败数
 * @param baselineVersion 基线版本
 * @param regressionCreated 是否因异常回滚自动创建回归用例
 */
public record NightlyBenchmarkResult(Instant night, boolean passed, long durationMs, int totalRuns,
                                     int failedRuns, String baselineVersion, boolean regressionCreated) {
}
