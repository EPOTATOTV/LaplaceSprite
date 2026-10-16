package io.laplace.sprite.adapters.hybrid;

import io.laplace.sprite.adapters.event.BukkitEventBridge;
import io.laplace.sprite.adapters.event.ModLoaderEventBridge;
import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.spi.EventBridge;

/**
 * 混合端事件桥（14.3 组合适配层第二件事）：双事件总线监听。
 *
 * <p>Bukkit Listener 与宿主模组加载器事件总线各自归一并合并，合并处用
 * {@link HybridEventDeduplicator} 折叠掉事件双发。两条总线复用的是家族的归一化实现，
 * 混合端自身不重写扫描逻辑。</p>
 */
public final class HybridEventBridge implements EventBridge {

    private final PlatformFamily bukkitFamily;
    private final PlatformFamily loaderFamily;
    private final HybridEventDeduplicator deduplicator = new HybridEventDeduplicator();

    private volatile int lastDeduplicatedCount;

    public HybridEventBridge(PlatformFamily bukkitFamily, PlatformFamily loaderFamily) {
        this.bukkitFamily = bukkitFamily;
        this.loaderFamily = loaderFamily;
    }

    public PlatformFamily bukkitFamily() {
        return bukkitFamily;
    }

    public PlatformFamily loaderFamily() {
        return loaderFamily;
    }

    /** 最近一次 bridge 折叠掉的重复事件数。 */
    public int lastDeduplicatedCount() {
        return lastDeduplicatedCount;
    }

    @Override
    public EventStream bridge(PlatformKey key) {
        EventStream bukkit = new BukkitEventBridge(bukkitFamily).bridge(key);
        EventStream mods = new ModLoaderEventBridge(loaderFamily).bridge(key);
        HybridEventDeduplicator.Result result = deduplicator.merge(bukkit.events(), mods.events());
        lastDeduplicatedCount = result.dropped();
        return new InMemoryEventStream(result.events());
    }
}
