package io.laplace.sprite.core.spi;

/**
 * 生命周期（14.4 ③）：onLoad → onEnable → onTick → onShutdown。
 *
 * <p>装配验证按冷启动全生命周期设计，不依赖 /reload 热加载语义（14.5）。</p>
 */
public interface LifecycleHook {

    /** 装载元数据、校验依赖与版本矩阵。 */
    void onLoad(InstallContext ctx);

    /** 注册事件桥与探针。 */
    void onEnable(InstallContext ctx);

    /** 每 tick 采样（仅真实环境轨）。 */
    default void onTick(TickContext ctx) {
    }

    /** 卸载与证据收尾。 */
    void onShutdown(InstallContext ctx);
}
