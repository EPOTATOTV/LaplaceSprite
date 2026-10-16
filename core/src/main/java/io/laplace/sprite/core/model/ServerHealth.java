package io.laplace.sprite.core.model;

/**
 * 实例健康（11.3 实例状态卡 / 12.2 TestServerHost）。
 *
 * @param instanceId      实例 id
 * @param platform        平台
 * @param tps             tick 数/秒（满值 20.0）
 * @param mspt            每 tick 耗时
 * @param memoryUsedMb    已用堆
 * @param memoryMaxMb     堆上限
 * @param players         真实在线玩家
 * @param simulatedPlayers 模拟玩家数
 * @param pluginVersion   被测插件版本
 * @param status          运行状态
 */
public record ServerHealth(String instanceId, PlatformKey platform, double tps, double mspt,
                           long memoryUsedMb, long memoryMaxMb, int players, int simulatedPlayers,
                           String pluginVersion, RunStatus status) {

    /** 健康阈值（12.3）：TPS 满值、MSPT <50ms、堆 ≤70%。 */
    public boolean healthy() {
        return tps >= 19.5 && mspt < 50.0 && (memoryMaxMb == 0 || memoryUsedMb * 100 / memoryMaxMb <= 70);
    }
}
