package io.laplace.sprite.adapters.event;

import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.spi.EventBridge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Velocity 事件桥（14.6）。
 *
 * <p>{@code EventTask.async} 提交后事件不算落流，只有 {@code Continuation} 恢复（{@link #resume}）
 * 才把结果补进流；这样断言层看不到「半完成」的会话状态。</p>
 */
public final class VelocityEventBridge implements EventBridge {

    private final Map<String, String> pendingTasks = new LinkedHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger();

    @Override
    public EventStream bridge(PlatformKey key) {
        return SyntheticStreams.proxy(SyntheticStreams.PLAYER, key);
    }

    /** 提交异步任务：返回任务 id，事件挂起。 */
    public String asyncTask(String kind, String playerId) {
        String taskId = "task-" + sequence.incrementAndGet();
        pendingTasks.put(taskId, playerId);
        return taskId;
    }

    /** Continuation 恢复：挂起的事件此刻才落流。 */
    public List<Event> resume(PlatformKey key) {
        List<Event> landed = new ArrayList<>();
        long tick = 20L;
        for (String playerId : pendingTasks.values()) {
            landed.add(new Event.ProxyPlayerJoin(playerId, tick, SyntheticStreams.timestamp(tick), key,
                    "lobby"));
            tick++;
        }
        pendingTasks.clear();
        return List.copyOf(landed);
    }

    public int pendingEvents() {
        return pendingTasks.size();
    }
}
