package io.laplace.sprite.core.plugin;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 内核事件总线：钩子过滤、异常隔离、退订、载荷不可变。 */
class KernelEventBusTest {

    @Test
    void publishesOnlyToListenersOfTheSameHook() {
        KernelEventBus bus = new KernelEventBus();
        List<String> topics = new ArrayList<>();
        bus.subscribe(KernelHook.ENTITY_TICK, event -> topics.add(event.topic()));

        bus.publish(event(KernelHook.DATAPACK_RUN, "datapack"));
        bus.publish(event(KernelHook.ENTITY_TICK, "tick"));

        assertEquals(List.of("tick"), topics);
    }

    @Test
    void listenerFailureIsIsolatedAndRecorded() {
        KernelEventBus bus = new KernelEventBus();
        AtomicInteger delivered = new AtomicInteger();
        bus.subscribe(KernelHook.TEST_LIFECYCLE, event -> {
            throw new IllegalStateException("boom");
        });
        bus.subscribe(KernelHook.TEST_LIFECYCLE, event -> delivered.incrementAndGet());

        bus.publish(event(KernelHook.TEST_LIFECYCLE, "suite"));

        assertEquals(1, delivered.get(), "前一个监听器抛异常不能中断后一个");
        assertEquals(1, bus.errors().size());
        assertTrue(bus.errors().get(0).contains("boom"));
    }

    @Test
    void closingSubscriptionStopsDelivery() throws Exception {
        KernelEventBus bus = new KernelEventBus();
        AtomicInteger delivered = new AtomicInteger();
        AutoCloseable subscription = bus.subscribe(KernelHook.RENDER_FRAME,
                event -> delivered.incrementAndGet());

        bus.publish(event(KernelHook.RENDER_FRAME, "frame"));
        subscription.close();
        bus.publish(event(KernelHook.RENDER_FRAME, "frame"));

        assertEquals(1, delivered.get());
        assertTrue(bus.errors().isEmpty());
    }

    @Test
    void payloadIsCopiedIntoAnImmutableSnapshot() {
        Map<String, Object> source = new HashMap<>();
        source.put("suite", "nightly");
        KernelEvent event = new KernelEvent(KernelHook.TEST_LIFECYCLE, "pacc",
                Instant.parse("2026-10-24T00:00:00Z"), source);

        source.put("suite", "changed");

        assertEquals("nightly", event.payload().get("suite"));
        assertThrows(UnsupportedOperationException.class, () -> event.payload().put("extra", 1));
    }

    private static KernelEvent event(KernelHook hook, String topic) {
        return new KernelEvent(hook, topic, Instant.now(), Map.of());
    }
}
