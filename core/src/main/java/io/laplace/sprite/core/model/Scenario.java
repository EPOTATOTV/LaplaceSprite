package io.laplace.sprite.core.model;

import java.util.List;
import java.util.Map;

/**
 * 场景剧本（4.1 ScenarioLibrary）。
 *
 * @param id              剧本 id（pvp / mining / bridge-sprint / reconnect / respawn / warmup / low-tps）
 * @param name            展示名
 * @param profiles        涉及的行为模型
 * @param networkModelRef 网络模型 id
 * @param simulatedPlayers 模拟玩家数
 * @param steps           剧本步骤（平台无关）
 */
public record Scenario(String id, String name, List<BehaviorProfile> profiles, String networkModelRef,
                       int simulatedPlayers, Map<String, Object> steps) {

    public Scenario {
        profiles = profiles == null ? List.of() : List.copyOf(profiles);
        steps = steps == null ? Map.of() : Map.copyOf(steps);
    }
}
