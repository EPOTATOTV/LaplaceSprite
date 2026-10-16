package io.laplace.sprite.simulator;

import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.BehaviorProfile;
import io.laplace.sprite.core.model.LegitTrajectory;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.model.Scenario;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 合法轨迹生成器（第五章 LegitTrajectory）：按行为模型产出可复现的逐 tick 事件流。
 *
 * <p>随机化只来自 seed，同 seed 得到逐字段相同的事件序列。</p>
 */
public final class LegitTrajectoryGenerator {

    private final BehaviorModelRegistry registry;
    private final ScenarioLibrary scenarios;

    public LegitTrajectoryGenerator() {
        this(new BehaviorModelRegistry(), new ScenarioLibrary());
    }

    public LegitTrajectoryGenerator(BehaviorModelRegistry registry, ScenarioLibrary scenarios) {
        this.registry = registry;
        this.scenarios = scenarios;
    }

    public LegitTrajectory generate(String id, BehaviorProfile profile, String scenarioId, long seed,
                                    int ticks, PlatformKey platform) {
        List<Event> events = registry.of(profile).generate(id, platform, 0L, new Random(seed), ticks);
        return new LegitTrajectory(id, profile, scenarioId, seed, events, networkRef(scenarioId));
    }

    /** 生成 count 个玩家：playerId 为 legit-&lt;index&gt;，种子为 seed + index。 */
    public List<LegitTrajectory> cohort(int count, BehaviorProfile profile, String scenarioId, long seed,
                                        PlatformKey platform, int ticks) {
        List<LegitTrajectory> cohort = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            cohort.add(generate("legit-" + index, profile, scenarioId, seed + index, ticks, platform));
        }
        return cohort;
    }

    private String networkRef(String scenarioId) {
        return scenarios.byId(scenarioId).map(Scenario::networkModelRef).orElse("default");
    }
}
