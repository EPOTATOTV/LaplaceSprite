package io.laplace.sprite.adapters.event;

import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.event.EventType;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.spi.EventBridge;

/**
 * Folia 事件桥（14.6）：与 Bukkit 家族同一批事件，额外产出区域 tick。
 *
 * <p>区域 id 同时进 {@code RegionTick.regionId} 与 {@code context()}，断言层不必知道区域调度细节。</p>
 */
public final class FoliaEventBridge implements EventBridge {

    @Override
    public EventStream bridge(PlatformKey key) {
        InMemoryEventStream out = SyntheticStreams.folia(SyntheticStreams.PLAYER, key);
        return out;
    }

    /** 区域数：`region-0-0` / `region-0-1` 两个区域各一次 tick。 */
    public int regionCount(EventStream stream) {
        return (int) stream.ofType(EventType.REGION_TICK).stream()
                .map(event -> event.context().get("regionId"))
                .distinct()
                .count();
    }

    /** 供报告标注：本桥对应的家族集合。 */
    public PlatformFamily family() {
        return PlatformFamily.FOLIA;
    }
}
