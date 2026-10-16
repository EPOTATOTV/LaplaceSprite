package io.laplace.sprite.simulator;

import io.laplace.sprite.core.model.BehaviorProfile;
import io.laplace.sprite.core.model.Scenario;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 内置场景剧本（4.1 场景剧本 / 5 Scenario）。
 *
 * <p>剧本只描述平台无关的玩家构成与步骤参数，网络模型用 id 引用（default/wifi/lte）。</p>
 */
public final class ScenarioLibrary {

    private static final List<Scenario> BUILT_IN = List.of(
            pvp(), mining(), bridgeSprint(), reconnect(), respawn(), warmup(), lowTps());

    public List<Scenario> builtIn() {
        return BUILT_IN;
    }

    public Optional<Scenario> byId(String id) {
        return BUILT_IN.stream().filter(scenario -> scenario.id().equals(id)).findFirst();
    }

    /** 默认剧本为 pvp。 */
    public Scenario defaultScenario() {
        return byId("pvp").orElseThrow();
    }

    private static Scenario pvp() {
        return new Scenario("pvp", "竞技对抗",
                List.of(BehaviorProfile.SPRINT, BehaviorProfile.CLICKING, BehaviorProfile.TARGET_SWITCH,
                        BehaviorProfile.MULTI_ENTITY, BehaviorProfile.KNOCKBACK_RESPONSE),
                "default", 8,
                Map.of("durationTicks", 1200, "rounds", 5, "arena", "quartz",
                        "strafe", true, "reachLimit", 3.0));
    }

    private static Scenario mining() {
        return new Scenario("mining", "矿洞采集",
                List.of(BehaviorProfile.MINE, BehaviorProfile.WALK, BehaviorProfile.SNEAK,
                        BehaviorProfile.INVENTORY_SORT),
                "default", 4,
                Map.of("depth", 32, "veins", 6, "tool", "netherite_pickaxe", "torchSpacing", 8));
    }

    private static Scenario bridgeSprint() {
        return new Scenario("bridge-sprint", "搭路冲刺",
                List.of(BehaviorProfile.GODBRIDGE, BehaviorProfile.TELLY, BehaviorProfile.NINJA,
                        BehaviorProfile.SPRINT),
                "wifi", 6,
                Map.of("lengthBlocks", 128, "gapWidth", 1, "blocksPerSecond", 5.0, "fallRecovery", true));
    }

    private static Scenario reconnect() {
        return new Scenario("reconnect", "断线重连",
                List.of(BehaviorProfile.WALK, BehaviorProfile.SPRINT),
                "lte", 2,
                Map.of("dropAfterTicks", 300, "reconnectDelayMs", 2000, "retries", 3));
    }

    private static Scenario respawn() {
        return new Scenario("respawn", "死亡重生",
                List.of(BehaviorProfile.WALK, BehaviorProfile.JUMP, BehaviorProfile.MINE),
                "default", 3,
                Map.of("respawnDelayTicks", 20, "spawnRadius", 16, "keepInventory", false));
    }

    private static Scenario warmup() {
        return new Scenario("warmup", "登录预热",
                List.of(BehaviorProfile.WALK, BehaviorProfile.SNEAK, BehaviorProfile.CLICKING),
                "wifi", 4,
                Map.of("warmupTicks", 200, "keepAliveSeconds", 15, "chunkRadius", 8));
    }

    private static Scenario lowTps() {
        return new Scenario("low-tps", "低 TPS 压测",
                List.of(BehaviorProfile.SPRINT, BehaviorProfile.JUMP, BehaviorProfile.SWIM,
                        BehaviorProfile.BOAT),
                "lte", 12,
                Map.of("targetTps", 12.0, "msptBudget", 50.0, "spawnRadius", 24, "durationTicks", 2400));
    }
}
