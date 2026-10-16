package io.laplace.sprite.profile;

/**
 * /spark health 的解析结果（15.3）：JVM 资源四件套。字段都可能为 null。
 *
 * @param heapUsedMb    堆占用（MB）
 * @param heapMaxMb     堆上限（MB）
 * @param gcPauseP99Ms  GC 停顿 p99（ms）
 * @param gcPauseMaxMs  GC 停顿最大值（ms）
 * @param cpuPercent    CPU 占用（%）
 * @param diskPercent   磁盘占用（%）
 */
public record SparkHealth(Long heapUsedMb, Long heapMaxMb, Double gcPauseP99Ms, Double gcPauseMaxMs,
                          Double cpuPercent, Double diskPercent) {

    public static SparkHealth empty() {
        return new SparkHealth(null, null, null, null, null, null);
    }

    public boolean present() {
        return heapUsedMb != null || gcPauseP99Ms != null || cpuPercent != null || diskPercent != null;
    }
}
