package io.laplace.sprite.servers;

import io.laplace.sprite.core.model.MetricSample;

import java.time.Instant;

/**
 * 合成指标画像（12.3 MetricsAgent 离线模式）。
 *
 * <p>没有真实数据源时按本画像产出确定性序列：第 n 次采样带动 n 步的漂移，
 * 因此性能面板、拐点告警与门禁在离线环境里同样可演练。</p>
 *
 * @param name              画像名
 * @param baseTps           起始 TPS
 * @param baseMspt          起始 MSPT
 * @param tpsDropPerStep    每步 TPS 降幅
 * @param msptRisePerStep   每步 MSPT 增幅
 * @param baseHeapMb        起始堆占用
 * @param maxHeapMb         堆上限
 * @param gcPauseMs         起始 GC 停顿
 * @param cpuPercent        起始 CPU
 * @param memoryPercent     起始内存占比
 * @param baseConnections   起始连接数
 * @param chunksLoaded      起始已加载区块
 * @param baseBandwidthKbps 起始带宽（Kbps）
 * @param entitiesPerChunk  每区块平均实体数
 * @param pingP50Ms         起始 Ping P50
 * @param pingP95Ms         起始 Ping P95
 * @param pingRisePerStep   每步 Ping 增幅
 */
public record SyntheticProfile(String name, double baseTps, double baseMspt, double tpsDropPerStep,
                               double msptRisePerStep, long baseHeapMb, long maxHeapMb, double gcPauseMs,
                               double cpuPercent, double memoryPercent, int baseConnections,
                               int chunksLoaded, double baseBandwidthKbps, int entitiesPerChunk,
                               double pingP50Ms, double pingP95Ms, double pingRisePerStep) {

    public MetricSample sample(int step, Instant at) {
        double tps = Math.max(0.0, baseTps - tpsDropPerStep * step);
        double mspt = baseMspt + msptRisePerStep * step;
        long heap = Math.min(maxHeapMb, baseHeapMb + step * 64L);
        int chunks = chunksLoaded + step * 3;
        int entities = chunks * entitiesPerChunk;
        double p50 = pingP50Ms + pingRisePerStep * step;
        // P95 至少等于 P50，避免画像参数把分位数写反。
        double p95 = Math.max(p50, pingP95Ms + pingRisePerStep * 1.6 * step);
        return new MetricSample(at, tps, mspt, heap, maxHeapMb, gcPauseMs + step * 0.5,
                Math.min(100.0, cpuPercent + step * 1.5), memoryPercent, baseConnections + step,
                chunks, baseBandwidthKbps + step * 16.0, entities, p50, p95);
    }

    /** 稳态：TPS 恒 20、MSPT 恒 12，延迟在门禁内，永不触发拐点。 */
    public static SyntheticProfile steady() {
        return new SyntheticProfile("steady", 20.0, 12.0, 0.0, 0.0, 512, 4096, 4.0, 25.0, 30.0, 0, 400,
                3200.0, 6, 28.0, 96.0, 0.0);
    }

    /** MSPT 斜坡：第 5 步越过 50ms（12 + 8×5 = 52），拐点正好落在第 5 步。 */
    public static SyntheticProfile msptRamp() {
        return new SyntheticProfile("mspt-ramp", 20.0, 12.0, 0.0, 8.0, 512, 4096, 4.0, 25.0, 30.0, 0,
                400, 3200.0, 6, 28.0, 96.0, 2.0);
    }

    /** 阶梯负载画像：连接数随台阶增长，带宽 / 区块 / 实体 / 延迟同步上升。 */
    public static SyntheticProfile loadRamp(int simulatedPlayers) {
        return new SyntheticProfile("load-" + simulatedPlayers, 20.0, 10.0 + simulatedPlayers / 2.0, 0.0,
                simulatedPlayers / 25.0, 512, 8192, 4.0, 20.0 + simulatedPlayers / 4.0, 35.0,
                simulatedPlayers, 400 + simulatedPlayers * 10, 3200.0 + simulatedPlayers * 12.0, 6,
                26.0 + simulatedPlayers / 40.0, 90.0 + simulatedPlayers / 5.0, 0.6);
    }
}
