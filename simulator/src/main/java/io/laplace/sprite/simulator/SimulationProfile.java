package io.laplace.sprite.simulator;

import io.laplace.sprite.core.model.BehaviorProfile;
import io.laplace.sprite.core.model.Scenario;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 场景玩家行为配比：把场景声明的行为模型按人数铺开，供 CLI 决定每个模拟玩家跑哪种行为。
 *
 * @param scenarioId 场景 id
 * @param mix        行为模型 -> 人数
 */
public record SimulationProfile(String scenarioId, Map<BehaviorProfile, Integer> mix) {

    public SimulationProfile {
        mix = mix == null ? Map.of() : Map.copyOf(mix);
    }

    /** 取场景声明的行为模型与玩家数做均分，缺省回落到默认剧本。 */
    public static SimulationProfile of(String scenarioId) {
        ScenarioLibrary library = new ScenarioLibrary();
        Scenario scenario = library.byId(scenarioId).orElseGet(library::defaultScenario);
        return new SimulationProfile(scenario.id(), distribute(scenario.profiles(), scenario.simulatedPlayers()));
    }

    /** 铺开配比后打乱，得到 players 长度的行为轮转序列；人数超出配比时循环补齐。 */
    public List<BehaviorProfile> rotation(int players, long seed) {
        List<BehaviorProfile> bag = new ArrayList<>();
        mix.forEach((profile, count) -> {
            for (int i = 0; i < count; i++) {
                bag.add(profile);
            }
        });
        if (bag.isEmpty()) {
            return List.of();
        }
        Collections.shuffle(bag, new Random(seed));
        List<BehaviorProfile> rotation = new ArrayList<>(players);
        for (int i = 0; i < players; i++) {
            rotation.add(bag.get(i % bag.size()));
        }
        return rotation;
    }

    private static Map<BehaviorProfile, Integer> distribute(List<BehaviorProfile> profiles, int players) {
        Map<BehaviorProfile, Integer> mix = new EnumMap<>(BehaviorProfile.class);
        if (profiles.isEmpty()) {
            return mix;
        }
        for (int i = 0; i < players; i++) {
            mix.merge(profiles.get(i % profiles.size()), 1, Integer::sum);
        }
        return mix;
    }
}
