package io.laplace.sprite.core.plugin;

/**
 * 内核事件钩子（技术文档 v3.0 L3-01）。
 *
 * <p>内核只在四个位置打点：数据包执行、实体 tick、渲染帧、测试生命周期。
 * 业务插件通过 {@link KernelEventBus} 订阅这些钩子，不直接依赖各模块实现。</p>
 */
public enum KernelHook {
    /** 数据包跑批前后。 */
    DATAPACK_RUN,
    /** 实体每 tick 推进。 */
    ENTITY_TICK,
    /** 客户端渲染帧。 */
    RENDER_FRAME,
    /** 测试运行的生命周期节点（启动、结束、清理）。 */
    TEST_LIFECYCLE
}
