package io.laplace.sprite.adapters;

/**
 * 适配器实现状态（14.2 / 14.3 接入矩阵的「状态」列）。
 *
 * <p>状态只描述接入深度，不参与运行时判定；矩阵门禁与回退由 {@code AdapterRegistry} 决定。</p>
 */
public enum AdapterStatus {

    /** 一级实现：接口形态已冻结，全部 SPI 落地。 */
    IMPLEMENTED_TIER1,
    /** 已实现：SPI 可用，少量平台特有行为待补。 */
    IMPLEMENTED,
    /** 兼容外壳：依赖兼容链回退到上游平台适配器。 */
    COMPAT_SHELL,
    /** 并发变体：与上游同 API，但调度模型不同（Folia 区域并行）。 */
    CONCURRENT_VARIANT,
    /** 生命周期终止：不再跟进新版本。 */
    EOL,
    /** 适配中：骨架已登记，行为矩阵未开放。 */
    IN_PROGRESS,
    /** 挂载在宿主平台上运行（如 Geyser 挂在 Bukkit 家族服务端上）。 */
    HOST_MOUNTED
}
