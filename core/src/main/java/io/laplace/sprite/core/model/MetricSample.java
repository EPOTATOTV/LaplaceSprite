package io.laplace.sprite.core.model;

import java.time.Instant;

/**
 * 性能采样点（12.3 MetricsAgent，5s 采样）。
 *
 * <p>连接数 / 带宽 / 区块 / 实体对应 12.3 的世界负载，Ping 分位对应网络延迟分布。未采集的字段记 0，
 * 由 {@link #hasLatencySample()} 区分「0ms」与「没测」。</p>
 *
 * @param at            采样时间
 * @param tps           瞬时 TPS
 * @param mspt          每 tick 耗时（ms）
 * @param heapUsedMb    已用堆
 * @param heapMaxMb     堆上限
 * @param gcPauseMs     GC 停顿
 * @param cpuPercent    CPU 占用
 * @param memoryPercent 内存占比
 * @param connections   在线连接数
 * @param chunksLoaded  已加载区块数
 * @param bandwidthKbps 带宽（Kbps）
 * @param entities      实体数
 * @param pingP50Ms     该采样窗口的 Ping P50
 * @param pingP95Ms     该采样窗口的 Ping P95
 */
public record MetricSample(Instant at, double tps, double mspt, long heapUsedMb, long heapMaxMb,
                           double gcPauseMs, double cpuPercent, double memoryPercent, int connections,
                           int chunksLoaded, double bandwidthKbps, int entities, double pingP50Ms,
                           double pingP95Ms) {

    /** 兼容构造：旧数据源没有带宽 / 实体 / Ping，按 0 记未采集。 */
    public MetricSample(Instant at, double tps, double mspt, long heapUsedMb, long heapMaxMb,
                        double gcPauseMs, double cpuPercent, double memoryPercent, int connections,
                        int chunksLoaded) {
        this(at, tps, mspt, heapUsedMb, heapMaxMb, gcPauseMs, cpuPercent, memoryPercent, connections,
                chunksLoaded, 0.0, 0, 0.0, 0.0);
    }

    /** 拐点判定：TPS 跌破 20.0 或 MSPT 持续 >50ms（12.3 ⑤）。 */
    public boolean knee() {
        return tps < 20.0 || mspt > 50.0;
    }

    /** 该窗口是否上报过 Ping；P50/P95 都为 0 视为未采集，而不是 0ms。 */
    public boolean hasLatencySample() {
        return pingP50Ms > 0.0 || pingP95Ms > 0.0;
    }
}
