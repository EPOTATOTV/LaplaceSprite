package io.laplace.sprite.adapters.hybrid;

import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.event.EventType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 双事件总线的重复事件折叠（14.3 组合适配层第二件事：事件双发）。
 *
 * <p>混合端上模组事件与 Bukkit Listener 会对同一动作各发一次，两条总线归一到相同核心事件后
 * 会重复进流。去重键取「类型 + 玩家 + tick + 载荷」，同一 tick 内同类型同载荷只保留一条；
 * 载荷不同的（例如两条内容不同的聊天）视为真实的两件事，不折叠。</p>
 */
public final class HybridEventDeduplicator {

    /** 去重结果：折叠后的事件与被折叠条数。 */
    public record Result(List<Event> events, int dropped) {
    }

    /**
     * 去重键。{@code payload} 直接放事件本体：同一混合端下两条总线用同一个 {@code PlatformKey}，
     * 因此记录相等即「类型 + 玩家 + tick + 时间戳 + 载荷」全同。
     */
    private record EventKey(EventType type, String playerId, long tick, Event payload) {
    }

    /** 保序去重，先出现的事件保留。 */
    public Result deduplicate(List<Event> events) {
        Map<EventKey, Event> unique = new LinkedHashMap<>();
        int dropped = 0;
        for (Event event : events) {
            EventKey key = new EventKey(event.type(), event.playerId(), event.tick(), event);
            if (unique.putIfAbsent(key, event) != null) {
                dropped++;
            }
        }
        return new Result(List.copyOf(unique.values()), dropped);
    }

    /** 两条总线归一流合并后折叠，返回保留的事件与折叠数。 */
    public Result merge(List<Event> firstBus, List<Event> secondBus) {
        List<Event> merged = new ArrayList<>(firstBus.size() + secondBus.size());
        merged.addAll(firstBus);
        merged.addAll(secondBus);
        return deduplicate(merged);
    }
}
