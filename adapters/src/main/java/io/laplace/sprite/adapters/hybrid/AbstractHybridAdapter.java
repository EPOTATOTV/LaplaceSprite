package io.laplace.sprite.adapters.hybrid;

import io.laplace.sprite.adapters.AbstractLaplaceAdapter;
import io.laplace.sprite.adapters.PlatformFacts;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.spi.EventBridge;
import io.laplace.sprite.core.spi.InstallLayout;
import io.laplace.sprite.core.spi.SupportRange;

/**
 * 混合端公共骨架：组合适配层只做三件事——双目录合并、双事件总线监听、跨生态 ID 映射校验。
 *
 * <p>前两件在这里落地（{@link HybridInstallLayout} 与 {@link HybridEventBridge}），第三件由
 * {@link CrossEcosystemIdMapper} 承担，装配阶段调用。具体适配器只剩「宿主是谁、支持哪些版本」。</p>
 */
public abstract class AbstractHybridAdapter extends AbstractLaplaceAdapter {

    private final PlatformFamily bukkitFamily;
    private final PlatformFamily loaderFamily;
    private final HybridInstallLayout layout = new HybridInstallLayout();

    protected AbstractHybridAdapter(String id, PlatformFamily family, SupportRange supportRange,
                                    PlatformFacts facts, PlatformFamily bukkitFamily,
                                    PlatformFamily loaderFamily) {
        super(id, family, supportRange, facts);
        this.bukkitFamily = bukkitFamily;
        this.loaderFamily = loaderFamily;
    }

    @Override
    public InstallLayout installLayout() {
        return layout;
    }

    @Override
    public EventBridge eventBridge() {
        return new HybridEventBridge(bukkitFamily, loaderFamily);
    }

    public HybridInstallLayout hybridInstallLayout() {
        return layout;
    }

    public PlatformFamily bukkitFamily() {
        return bukkitFamily;
    }

    public PlatformFamily loaderFamily() {
        return loaderFamily;
    }
}
