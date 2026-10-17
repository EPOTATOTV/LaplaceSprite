package io.laplace.sprite.core.model;

/**
 * 实例健康（11.3 实例状态卡 / 12.2 TestServerHost）。
 *
 * <p>连接数 / 带宽 / 区块 / 实体 / Ping 分位来自该实例挂载的 MetricsAgent，对应 12.3 的
 * 连接与带宽、世界负载、网络延迟分布三行。</p>
 *
 * @param instanceId       实例 id
 * @param platform         平台
 * @param tps              tick 数/秒（满值 20.0）
 * @param mspt             每 tick 耗时
 * @param memoryUsedMb     已用堆
 * @param memoryMaxMb      堆上限
 * @param players          真实在线玩家
 * @param simulatedPlayers 模拟玩家数
 * @param pluginVersion    被测插件版本
 * @param status           运行状态
 * @param connections      在线连接数
 * @param bandwidthKbps    带宽（Kbps）
 * @param chunksLoaded     已加载区块数
 * @param entities         实体数
 * @param pingP50Ms        Ping P50
 * @param pingP95Ms        Ping P95
 */
public record ServerHealth(String instanceId, PlatformKey platform, double tps, double mspt,
                           long memoryUsedMb, long memoryMaxMb, int players, int simulatedPlayers,
                           String pluginVersion, RunStatus status, int connections,
                           double bandwidthKbps, int chunksLoaded, int entities, double pingP50Ms,
                           double pingP95Ms) {

    /** 兼容构造：未采集的 12.3 扩展字段记 0。 */
    public ServerHealth(String instanceId, PlatformKey platform, double tps, double mspt,
                        long memoryUsedMb, long memoryMaxMb, int players, int simulatedPlayers,
                        String pluginVersion, RunStatus status) {
        this(instanceId, platform, tps, mspt, memoryUsedMb, memoryMaxMb, players, simulatedPlayers,
                pluginVersion, status, 0, 0.0, 0, 0, 0.0, 0.0);
    }

    /** 健康阈值（12.3）：TPS 满值、MSPT <50ms、堆 ≤70%。 */
    public boolean healthy() {
        return tps >= 19.5 && mspt < 50.0 && (memoryMaxMb == 0 || memoryUsedMb * 100 / memoryMaxMb <= 70);
    }
}
