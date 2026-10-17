package io.laplace.sprite.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 网络延迟分布（12.3 网络延迟分布）。
 *
 * <p>分位采用 nearest-rank：升序排序后第 {@code ceil(p/100 × N)} 个样本即为分位值。样本少时它比线性
 * 插值保守（不会给出样本里不存在的延迟），适合门禁这种「宁可早报」的场景。</p>
 *
 * @param p50Ms       50 分位延迟
 * @param p95Ms       95 分位延迟
 * @param sampleCount 参与计算的 ping 样本数
 */
public record NetworkLatencyDistribution(double p50Ms, double p95Ms, int sampleCount) {

    /** 12.3 门禁：P50 严格小于 50ms。 */
    public static final double P50_LIMIT_MS = 50.0;
    /** 12.3 门禁：P95 严格小于 150ms。 */
    public static final double P95_LIMIT_MS = 150.0;

    public NetworkLatencyDistribution {
        if (sampleCount < 0) {
            throw new IllegalArgumentException("Ping 样本数不能为负数: " + sampleCount);
        }
        if (sampleCount > 0 && (!Double.isFinite(p50Ms) || !Double.isFinite(p95Ms))) {
            throw new IllegalArgumentException("Ping 分位必须是有限数: p50=" + p50Ms + " p95=" + p95Ms);
        }
        if (sampleCount > 0 && (p50Ms < 0.0 || p95Ms < 0.0)) {
            throw new IllegalArgumentException("Ping 延迟不能为负数: p50=" + p50Ms + " p95=" + p95Ms);
        }
        if (sampleCount > 0 && p95Ms < p50Ms) {
            throw new IllegalArgumentException("P95 不能小于 P50: p50=" + p50Ms + " p95=" + p95Ms);
        }
    }

    public static NetworkLatencyDistribution empty() {
        return new NetworkLatencyDistribution(0.0, 0.0, 0);
    }

    public boolean measured() {
        return sampleCount > 0;
    }

    /** 从原始 Ping 样本计算；空列表返回空分布，不伪造 0ms。 */
    public static NetworkLatencyDistribution of(List<Double> samplesMs) {
        List<Double> valid = new ArrayList<>();
        if (samplesMs != null) {
            for (Double value : samplesMs) {
                if (Objects.nonNull(value)) {
                    valid.add(value);
                }
            }
        }
        if (valid.isEmpty()) {
            return empty();
        }
        double[] sorted = valid.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        for (double value : sorted) {
            if (!Double.isFinite(value) || value < 0.0) {
                throw new IllegalArgumentException("Ping 延迟必须是有限非负数: " + value);
            }
        }
        return new NetworkLatencyDistribution(nearestRank(sorted, 50.0), nearestRank(sorted, 95.0),
                sorted.length);
    }

    public static NetworkLatencyDistribution of(double... samplesMs) {
        if (samplesMs == null || samplesMs.length == 0) {
            return empty();
        }
        List<Double> values = new ArrayList<>(samplesMs.length);
        for (double value : samplesMs) {
            values.add(value);
        }
        return of(values);
    }

    private static double nearestRank(double[] sorted, double percentile) {
        int rank = (int) Math.ceil(percentile / 100.0 * sorted.length);
        int index = Math.min(sorted.length - 1, Math.max(0, rank - 1));
        return sorted[index];
    }
}
