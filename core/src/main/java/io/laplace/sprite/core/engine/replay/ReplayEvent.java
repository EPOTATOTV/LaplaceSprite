package io.laplace.sprite.core.engine.replay;

import com.fasterxml.jackson.core.type.TypeReference;

import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.event.EventType;
import io.laplace.sprite.core.io.Json;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 可序列化的事件快照（4.6 导出 / 回放）。
 *
 * <p>事件是 sealed interface，直接序列化会丢子类型信息；这里保留 {@link EventType} 与完整字段表，
 * 读取时按类型还原成原 record，因此导出文件可以脱离原进程重放。</p>
 *
 * @param type 事件类型
 * @param data 事件 record 的全部字段
 */
public record ReplayEvent(EventType type, Map<String, Object> data) {

    public ReplayEvent {
        Objects.requireNonNull(type, "type");
        data = data == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }

    public static ReplayEvent from(Event event) {
        Map<String, Object> fields = Json.jsonMapper().convertValue(event,
                new TypeReference<Map<String, Object>>() {
                });
        return new ReplayEvent(event.type(), fields);
    }

    public Event toEvent() {
        return Json.jsonMapper().convertValue(data, eventClass());
    }

    public String playerId() {
        Object value = data.get("playerId");
        return value == null ? null : value.toString();
    }

    public long tick() {
        Object value = data.get("tick");
        return value instanceof Number number ? number.longValue() : 0L;
    }

    public Instant timestamp() {
        Object value = data.get("timestamp");
        if (value instanceof Instant instant) {
            return instant;
        }
        return value instanceof String text ? Instant.parse(text) : Instant.EPOCH;
    }

    private Class<? extends Event> eventClass() {
        return switch (type) {
            case PLAYER_JOIN -> Event.PlayerJoin.class;
            case PLAYER_LEAVE -> Event.PlayerLeave.class;
            case PROXY_PLAYER_JOIN -> Event.ProxyPlayerJoin.class;
            case PROXY_PLAYER_LEAVE -> Event.ProxyPlayerLeave.class;
            case PROXY_CHAT -> Event.ProxyChat.class;
            case DISCONNECT -> Event.Disconnect.class;
            case MOVE -> Event.Move.class;
            case COMBAT -> Event.Combat.class;
            case INTERACT -> Event.Interact.class;
            case CHAT -> Event.Chat.class;
            case INVENTORY -> Event.Inventory.class;
            case PACKET -> Event.Packet.class;
            case CHUNK_LOAD -> Event.ChunkLoad.class;
            case BLOCK_CHANGE -> Event.BlockChange.class;
            case DATA_CONSISTENCY -> Event.DataConsistency.class;
            case BRAND -> Event.Brand.class;
            case MOD_HASH -> Event.ModHash.class;
            case ADDON -> Event.Addon.class;
            case REGION_TICK -> Event.RegionTick.class;
            case TICK -> Event.Tick.class;
            case REGISTRY -> Event.Registry.class;
            case GAME_TEST_SUITE -> Event.GameTestSuite.class;
        };
    }
}
