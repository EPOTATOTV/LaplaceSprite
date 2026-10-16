package io.laplace.sprite.servers;

import io.laplace.sprite.core.model.MetricSample;

import java.time.Instant;

/**
 * 合成指标画像（12.3 MetricsAgent 离线模式）。
 *
 * <p>没有真实数据源时按本画像产出确定性序列：第 n 次采样带动 n 步的漂移，
 * 因此性能面板、拐点告警与门禁在离线环境里同样可演练。</p>
 *
 * @param name            画像名
 * @param baseTps         起始 TPS
 * @param baseMspt        起始 MSPT
 * @param tpsDropPerStep  每步 TPS 降幅
 * @param msptRisePerStep 每步 MSPT 增幅
 * @param baseHeapMb      起始堆占用
 * @param maxHeapMb       堆上限
 * @param gcPauseMs       起始 GC 停顿
 * @param cpuPercent      起始 CPU
 * @param memoryPercent   起始内存占比
 * @param baseConnections 起始连接数
 * @param chunksLoaded    起始已加载区块
 */
public record SyntheticProfile(String name, double baseTps, double baseMspt, double tpsDropPerStep,
                               double msptRisePerStep, long baseHeapMb, long maxHeapMb, double gcPauseMs,
                               double cpuPercent, double memoryPercent, int baseConnections,
                               int chunksLoaded) {

    public MetricSample sample(int step, Instant at) {
        double tps = Math.max(0.0, baseTps - tpsDropPerStep * step);
        double mspt = baseMspt + msptRisePerStep * step;
        long heap = Math.min(maxHeapMb, baseHeapMb + step * 64L);
        return new MetricSample(at, tps, mspt, heap, maxHeapMb, gcPauseMs + step * 0.5,
                Math.min(100.0, cpuPercent + step * 1.5), memoryPercent, baseConnections + step,
                chunksLoaded + step * 3);
    }

    /** 稳态：TPS 恒 20、MSPT 恒 12，永不触发拐点。 */
    public static SyntheticProfile steady() {
        return new SyntheticProfile("steady", 20.0, 12.0, 0.0, 0.0, 512, 4096, 4.0, 25.0, 30.0, 0, 400);
    }

    /** MSPT 斜坡：第 5 步越过 50ms（12 + 8×5 = 52），拐点正好落在第 5 步。 */
    public static SyntheticProfile msptRamp() {
        return new SyntheticProfile("mspt-ramp", 20.0, 12.0, 0.0, 8.0, 512, 4096, 4.0, 25.0, 30.0, 0, 400);
    }

    /** 阶梯负载画像：连接数随台阶增长，MSPT 同步上升。 */
    public static SyntheticProfile loadRamp(int simulatedPlayers) {
        return new SyntheticProfile("load-" + simulatedPlayers, 20.0, 10.0 + simulatedPlayers / 2.0, 0.0,
                simulatedPlayers / 25.0, 512, 8192, 4.0, 20.0 + simulatedPlayers / 4.0, 35.0,
                simulatedPlayers, 400 + simulatedPlayers * 10);
    }
}
