package io.laplace.sprite.cli;

import io.laplace.sprite.cheats.CheatCatalog;
import io.laplace.sprite.cheats.CheatSessionFactory;
import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.engine.SimulatedSession;
import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.model.BehaviorProfile;
import io.laplace.sprite.core.model.CheatSpec;
import io.laplace.sprite.core.model.CheatTierName;
import io.laplace.sprite.core.model.LegitTrajectory;
import io.laplace.sprite.core.model.NetworkModel;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.model.Scenario;
import io.laplace.sprite.network.NetworkModelRegistry;
import io.laplace.sprite.simulator.LegitSessionFactory;
import io.laplace.sprite.simulator.LegitTrajectoryGenerator;
import io.laplace.sprite.simulator.ScenarioLibrary;
import io.laplace.sprite.simulator.SimulationProfile;

import java.util.ArrayList;
import java.util.List;

/**
 * 把输入层各组件（场景库 / 合法模拟 / 作弊矩阵 / 网络模型）组装成待执行会话。
 *
 * <p>run 与 replay 共用；不承载任何判定逻辑。</p>
 */
final class SuiteFactory {

    private final ScenarioLibrary scenarios = new ScenarioLibrary();
    private final LegitTrajectoryGenerator generator = new LegitTrajectoryGenerator();
    private final LegitSessionFactory legitSessions = new LegitSessionFactory();
    private final CheatCatalog catalog = new CheatCatalog();
    private final CheatSessionFactory cheatSessions = new CheatSessionFactory();
    private final NetworkModelRegistry networks = new NetworkModelRegistry();

    Scenario scenario(String id) {
        return scenarios.byId(id).orElseGet(scenarios::defaultScenario);
    }

    boolean scenarioExists(String id) {
        return scenarios.byId(id).isPresent();
    }

    List<String> scenarioIds() {
        return scenarios.builtIn().stream().map(Scenario::id).toList();
    }

    NetworkModel network(Scenario scenario, long seed) {
        NetworkModel base = networks.byId(scenario.networkModelRef())
                .orElseGet(networks::defaultModel);
        return networks.withSeed(base, seed);
    }

    NetworkModel networkById(String id, long seed) {
        return networks.withSeed(networks.byId(id).orElseGet(networks::defaultModel), seed);
    }

    /** 逐玩家生成合法轨迹：行为模型按场景配比轮转，种子递增保证可复现。 */
    List<SimulatedSession> legit(int players, Scenario scenario, PlatformKey platform, long seed, int ticks) {
        SimulationProfile profile = SimulationProfile.of(scenario.id());
        List<BehaviorProfile> rotation = profile.rotation(players, seed);
        List<LegitTrajectory> trajectories = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            BehaviorProfile behavior = rotation.isEmpty()
                    ? BehaviorProfile.SPRINT
                    : rotation.get(i % rotation.size());
            trajectories.add(generator.generate("legit-" + i, behavior, scenario.id(),
                    seed + i, ticks, platform));
        }
        return legitSessions.toSessions(trajectories);
    }

    List<CheatSpec> specs(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return catalog.loadDefaults();
        }
        List<CheatSpec> selected = new ArrayList<>();
        for (String id : ids) {
            catalog.byId(id).ifPresentOrElse(selected::add,
                    () -> {
                        throw new IllegalArgumentException("未找到作弊样本: " + id);
                    });
        }
        return selected;
    }

    List<SimulatedSession> cheat(List<CheatSpec> specs, CheatTierName tier, Scenario scenario,
                                PlatformKey platform, NetworkModel network, long seed, EventStream baseline) {
        return cheatSessions.buildSessions(specs, tier, scenario.id(), platform, network, seed, baseline);
    }

    List<SimulatedSession> boundary(List<CheatSpec> specs, Scenario scenario, PlatformKey platform,
                                    NetworkModel network, long seed, EventStream baseline) {
        return cheatSessions.buildBoundarySessions(specs, scenario.id(), platform, network, seed, baseline);
    }

    /** 注入器的合法基线模板：取场景首个行为模型的一条轨迹。 */
    EventStream baseline(Scenario scenario, PlatformKey platform, long seed, int ticks) {
        BehaviorProfile behavior = scenario.profiles().isEmpty()
                ? BehaviorProfile.SPRINT
                : scenario.profiles().get(0);
        LegitTrajectory trajectory = generator.generate("baseline", behavior, scenario.id(), seed,
                ticks, platform);
        return new InMemoryEventStream(trajectory.events());
    }

    LegitTrajectory trajectory(String id, BehaviorProfile behavior, Scenario scenario, long seed,
                               int ticks, PlatformKey platform) {
        return generator.generate(id, behavior, scenario.id(), seed, ticks, platform);
    }
}
