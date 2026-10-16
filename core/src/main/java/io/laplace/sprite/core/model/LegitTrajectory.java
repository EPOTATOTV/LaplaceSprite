package io.laplace.sprite.core.model;

import io.laplace.sprite.core.event.Event;

import java.util.List;

/**
 * 合法会话定义（第五章 LegitTrajectory），可复现（seed 随机化）。
 *
 * @param id             轨迹 id
 * @param behaviorProfile 行为模型
 * @param scenario       场景剧本 id
 * @param seed           随机种子
 * @param events         逐 tick 事件流
 * @param networkModelRef 网络模型 id
 */
public record LegitTrajectory(
        String id,
        BehaviorProfile behaviorProfile,
        String scenario,
        long seed,
        List<Event> events,
        String networkModelRef) {

    public LegitTrajectory {
        events = events == null ? List.of() : List.copyOf(events);
    }

    public int eventCount() {
        return events.size();
    }
}
