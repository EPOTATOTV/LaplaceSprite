package io.laplace.sprite.adapters.event;

import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.spi.EventBridge;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Bukkit 家族事件桥（14.6）。
 *
 * <p>异步事件（AsyncChatEvent 这类）不能直接改世界状态，先落缓冲，由下一个主线程 tick 收口后再进流。</p>
 */
public final class BukkitEventBridge implements EventBridge {

    private final PlatformFamily family;
    private final List<Event> asyncForwarded = new CopyOnWriteArrayList<>();

    public BukkitEventBridge(PlatformFamily family) {
        this.family = family;
    }

    public PlatformFamily family() {
        return family;
    }

    @Override
    public EventStream bridge(PlatformKey key) {
        InMemoryEventStream out = SyntheticStreams.plugin(SyntheticStreams.PLAYER, key);
        out.emitAll(asyncForwarded);
        return out;
    }

    /** 异步事件入缓冲，不立即进流。 */
    public void forwardAsync(Event event) {
        asyncForwarded.add(event);
    }

    public int asyncForwardedCount() {
        return asyncForwarded.size();
    }
}
