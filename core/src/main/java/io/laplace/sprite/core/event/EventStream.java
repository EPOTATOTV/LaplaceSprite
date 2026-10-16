package io.laplace.sprite.core.event;

import java.util.List;
import java.util.OptionalDouble;
import java.util.stream.Stream;

/**
 * 归一化内部事件流（第五章 / 14.6）：唯一出口，检测引擎与场景剧本不感知具体平台。
 */
public interface EventStream {

    void emit(Event event);

    List<Event> events();

    int size();

    default List<Event> ofType(EventType type) {
        return events().stream().filter(e -> e.type() == type).toList();
    }

    default List<Event> ofPlayer(String playerId) {
        return events().stream().filter(e -> playerId.equals(e.playerId())).toList();
    }

    default Stream<Event> stream() {
        return events().stream();
    }

    default boolean isEmpty() {
        return size() == 0;
    }

    /** 最近一个 tick 事件里的 TPS，用于指标聚合。 */
    default OptionalDouble lastTps() {
        return events().stream()
                .filter(Event.Tick.class::isInstance)
                .map(Event.Tick.class::cast)
                .mapToDouble(Event.Tick::tps)
                .reduce((a, b) -> b);
    }
}
