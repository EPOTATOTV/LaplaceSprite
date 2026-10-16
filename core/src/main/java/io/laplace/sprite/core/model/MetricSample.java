package io.laplace.sprite.core.model;

import java.time.Instant;

/**
 * 性能采样点（12.3 MetricsAgent，5s 采样）。
 */
public record MetricSample(Instant at, double tps, double mspt, long heapUsedMb, long heapMaxMb,
                           double gcPauseMs, double cpuPercent, double memoryPercent, int connections,
                           int chunksLoaded) {

    /** 拐点判定：TPS 跌破 20.0 或 MSPT 持续 >50ms（12.3 ⑤）。 */
    public boolean knee() {
        return tps < 20.0 || mspt > 50.0;
    }
}
