package io.laplace.sprite.loadtest;

import io.laplace.sprite.core.model.MetricSample;

import java.time.Instant;

/**
 * 压测时间轴上的一个采样点（15.9），字段覆盖 12.3 的连接数 / 区块数 / 实体数。
 *
 * @param tick        采样 tick
 * @param concurrency 当前并发
 * @param tps         瞬时 TPS
 * @param msptP50     tick 耗时中位数（ms）
 * @param msptP95     tick 耗时 95 分位（ms）
 * @param msptMax     tick 耗时最大值（ms）
 * @param connections 连接数
 * @param chunks      已加载区块数
 * @param entities    实体数
 */
public record LoadSample(int tick, int concurrency, double tps, double msptP50, double msptP95,
                         double msptMax, int connections, int chunks, int entities) {

    public LoadSample {
        if (tick < 0) {
            throw new IllegalArgumentException("采样 tick 不能为负数: " + tick);
        }
        if (concurrency <= 0) {
            throw new IllegalArgumentException("采样并发必须为正数: " + concurrency);
        }
        if (tps < 0.0 || msptP50 < 0.0 || msptP95 < 0.0 || msptMax < 0.0) {
            throw new IllegalArgumentException("TPS / MSPT 不能为负数");
        }
        if (msptP95 < msptP50 || msptMax < msptP95) {
            throw new IllegalArgumentException("MSPT 分位数顺序不合法: p50=%s p95=%s max=%s"
                    .formatted(msptP50, msptP95, msptMax));
        }
        if (connections < 0 || chunks < 0 || entities < 0) {
            throw new IllegalArgumentException("连接数 / 区块数 / 实体数不能为负数");
        }
    }

    /**
     * 转成 12.3 的统一指标采样。
     *
     * <p>压测采样只覆盖 TPS / MSPT / 连接 / 区块；堆、GC、CPU 由调用方从 MetricsAgent 补入，
     * 这里不做默认值填充，避免把缺测数据伪装成实测。</p>
     */
    public MetricSample toMetricSample(Instant at, long heapUsedMb, long heapMaxMb, double gcPauseMs,
                                       double cpuPercent, double memoryPercent) {
        return new MetricSample(at, tps, msptP50, heapUsedMb, heapMaxMb, gcPauseMs, cpuPercent,
                memoryPercent, connections, chunks);
    }
}
