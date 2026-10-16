package io.laplace.sprite.adapters.event;

import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.spi.EventBridge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BungeeCord / Waterfall 事件桥（14.6）。
 *
 * <p>代理端事件要等意图完成才可见：{@code registerIntent} 挂起，{@code completeIntent} 才把
 * 归一化事件补进流。顺序因此稳定为 join → chat → leave。</p>
 */
public final class BungeeCordEventBridge implements EventBridge {

    private final PlatformFamily family;
    private final Map<String, Pending> pending = new LinkedHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger();

    public BungeeCordEventBridge(PlatformFamily family) {
        this.family = family;
    }

    public PlatformFamily family() {
        return family;
    }

    @Override
    public EventStream bridge(PlatformKey key) {
        InMemoryEventStream out = SyntheticStreams.core(SyntheticStreams.PLAYER, key);
        List<String> intents = new ArrayList<>();
        intents.add(registerIntent("join", SyntheticStreams.PLAYER, "lobby"));
        intents.add(registerIntent("chat", SyntheticStreams.PLAYER, "hello proxy"));
        intents.add(registerIntent("leave", SyntheticStreams.PLAYER, "lobby"));
        for (String intentId : intents) {
            completeIntent(intentId, key).ifPresent(out::emit);
        }
        return out;
    }

    /** 注册意图，返回意图 id；此时事件尚未落流。 */
    public String registerIntent(String kind, String playerId, String targetServer) {
        String intentId = "intent-" + sequence.incrementAndGet();
        pending.put(intentId, new Pending(kind, playerId, targetServer));
        return intentId;
    }

    /** 完成意图，产出对应的代理事件。 */
    public Optional<Event> completeIntent(String intentId, PlatformKey key) {
        Pending entry = pending.remove(intentId);
        if (entry == null) {
            return Optional.empty();
        }
        long tick = 9 + sequence.get();
        return switch (entry.kind()) {
            case "join" -> Optional.of(new Event.ProxyPlayerJoin(entry.playerId(), tick,
                    SyntheticStreams.timestamp(tick), key, entry.targetServer()));
            case "leave" -> Optional.of(new Event.ProxyPlayerLeave(entry.playerId(), tick,
                    SyntheticStreams.timestamp(tick), key, entry.targetServer(), "switch"));
            default -> Optional.of(new Event.ProxyChat(entry.playerId(), tick,
                    SyntheticStreams.timestamp(tick), key, entry.targetServer()));
        };
    }

    public int pendingCount() {
        return pending.size();
    }

    private record Pending(String kind, String playerId, String targetServer) {
    }
}
