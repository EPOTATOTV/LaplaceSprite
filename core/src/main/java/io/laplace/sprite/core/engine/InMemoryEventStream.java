package io.laplace.sprite.core.engine;

import io.laplace.sprite.core.event.Event;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 内存事件流实现（第五章）：并发灌入下状态不丢失（4.5 并发安全）。
 */
public final class InMemoryEventStream implements io.laplace.sprite.core.event.EventStream {

    private final CopyOnWriteArrayList<Event> events = new CopyOnWriteArrayList<>();

    public InMemoryEventStream() {
    }

    public InMemoryEventStream(List<Event> initial) {
        if (initial != null) {
            events.addAll(initial);
        }
    }

    @Override
    public void emit(Event event) {
        events.add(event);
    }

    public void emitAll(List<Event> batch) {
        events.addAll(batch);
    }

    @Override
    public List<Event> events() {
        return new ArrayList<>(events);
    }

    @Override
    public int size() {
        return events.size();
    }
}
