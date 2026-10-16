package io.laplace.sprite.core.engine;

import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.model.Thresholds;

/**
 * 一次运行的请求参数。
 *
 * @param runId       运行 id
 * @param targetId    检测目标 id
 * @param suite       套件名（legit / cheat / boundary / mixed）
 * @param platform    运行平台；进程内轨为空
 * @param seed        随机种子（可复现）
 * @param thresholds  门禁阈值
 * @param parallel    是否并发灌入（并发安全断言）
 * @param coverage    核心模块行覆盖率（由 JaCoCo 报告回填）
 */
public record RunRequest(String runId, String targetId, String suite, PlatformKey platform, long seed,
                         Thresholds thresholds, boolean parallel, double coverage) {

    public static RunRequest inProcess(String runId, String targetId, String suite, long seed) {
        return new RunRequest(runId, targetId, suite, null, seed, Thresholds.defaults(), false, 0.0);
    }

    public RunRequest withParallel(boolean value) {
        return new RunRequest(runId, targetId, suite, platform, seed, thresholds, value, coverage);
    }

    public RunRequest withCoverage(double value) {
        return new RunRequest(runId, targetId, suite, platform, seed, thresholds, parallel, value);
    }
}
