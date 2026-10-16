package io.laplace.sprite.simulator;

import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.BehaviorProfile;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BehaviorModelRegistryTest {

    private static final PlatformKey PLATFORM = PlatformKey.of(PlatformFamily.PAPER, "1.21");

    /** 单 tick 位移上限：最快的模型（击退初速 1.2 格/tick）之外留足余量。 */
    private static final double MAX_TICK_DISTANCE = 3.0;

    private final BehaviorModelRegistry registry = new BehaviorModelRegistry();

    @Test
    void registryCoversEveryBehaviorProfile() {
        assertEquals(Set.of(BehaviorProfile.values()), registry.supported());
        for (BehaviorProfile profile : BehaviorProfile.values()) {
            assertEquals(profile, registry.of(profile).profile());
        }
    }

    @Test
    void everyProfileEmitsEventsWithContinuousMovement() {
        for (BehaviorProfile profile : BehaviorProfile.values()) {
            List<Event> events = registry.of(profile).generate("legit-0", PLATFORM, 0L, new Random(7L), 200);
            assertFalse(events.isEmpty(), profile + " 未产出事件");
            assertTrue(events.stream().anyMatch(Event.Move.class::isInstance), profile + " 未产出 Move");
            assertTrue(events.stream().anyMatch(Event.Brand.class::isInstance), profile + " 未产出 Brand");
            assertTrue(events.stream().anyMatch(Event.Packet.class::isInstance), profile + " 未产出 Packet");

            Event.Move previous = null;
            for (Event event : events) {
                if (!(event instanceof Event.Move move)) {
                    continue;
                }
                if (previous != null) {
                    double distance = Math.sqrt(Math.pow(move.x() - previous.x(), 2)
                            + Math.pow(move.y() - previous.y(), 2)
                            + Math.pow(move.z() - previous.z(), 2));
                    assertTrue(distance <= MAX_TICK_DISTANCE,
                            profile + " 相邻 tick 位移过大: " + distance);
                }
                previous = move;
            }
        }
    }

    @Test
    void movingModelsKeepYawTurnsSmooth() {
        for (BehaviorProfile profile : List.of(BehaviorProfile.WALK, BehaviorProfile.SPRINT,
                BehaviorProfile.ELYTRA, BehaviorProfile.BOAT)) {
            List<Event.Move> moves = registry.of(profile)
                    .generate("legit-0", PLATFORM, 0L, new Random(21L), 120).stream()
                    .filter(Event.Move.class::isInstance)
                    .map(Event.Move.class::cast)
                    .toList();
            for (int i = 1; i < moves.size(); i++) {
                double delta = Math.abs(moves.get(i).yaw() - moves.get(i - 1).yaw());
                double turn = Math.min(delta, 360.0 - delta);
                assertTrue(turn <= 6.0, profile + " 转向跳变: " + turn);
            }
        }
    }

    @Test
    void bridgingModelsPlaceBlocksWhileMoving() {
        for (BehaviorProfile profile : List.of(BehaviorProfile.GODBRIDGE, BehaviorProfile.TELLY,
                BehaviorProfile.NINJA)) {
            List<Event> events = registry.of(profile).generate("legit-0", PLATFORM, 0L, new Random(3L), 80);
            assertTrue(events.stream().anyMatch(event -> event instanceof Event.Interact interact
                    && "place".equals(interact.action())), profile + " 未放方块");
            assertTrue(events.stream().anyMatch(Event.BlockChange.class::isInstance),
                    profile + " 未产出方块变化");
            assertTrue(events.stream().anyMatch(event -> event instanceof Event.Move move
                    && (move.sneaking() || move.sprinting())), profile + " 缺少潜行/疾跑标记");
        }
    }

    @Test
    void combatModelsStayWithinClickingBounds() {
        for (BehaviorProfile profile : List.of(BehaviorProfile.CLICKING, BehaviorProfile.TARGET_SWITCH,
                BehaviorProfile.MULTI_ENTITY)) {
            List<Event.Combat> combats = registry.of(profile)
                    .generate("legit-0", PLATFORM, 0L, new Random(11L), 120).stream()
                    .filter(Event.Combat.class::isInstance)
                    .map(Event.Combat.class::cast)
                    .toList();
            assertFalse(combats.isEmpty(), profile + " 未产出战斗事件");
            for (Event.Combat combat : combats) {
                assertTrue(combat.clicksPerSecond() >= 1 && combat.clicksPerSecond() <= 12,
                        "CPS 越界: " + combat.clicksPerSecond());
                assertTrue(combat.reach() >= 2.0 && combat.reach() <= 3.0,
                        "触及距离越过原版上限 3.0: " + combat.reach());
                assertTrue(List.of("single", "butterfly", "drag").contains(combat.swingType()),
                        "未知摆动方式: " + combat.swingType());
            }
        }
    }

    @Test
    void blockPlacingModelsLookDownAtThePlacementFace() {
        // 往脚下放方块必须低头（俯仰角接近 +90），否则原版准星取不到方块面，属于物理上不可能的合法动作。
        for (BehaviorProfile profile : List.of(BehaviorProfile.GODBRIDGE, BehaviorProfile.TELLY,
                BehaviorProfile.NINJA, BehaviorProfile.PLACE)) {
            List<Event.Move> moves = registry.of(profile)
                    .generate("legit-0", PLATFORM, 0L, new Random(23L), 120).stream()
                    .filter(Event.Move.class::isInstance)
                    .map(Event.Move.class::cast)
                    .toList();
            assertTrue(moves.stream().allMatch(move -> move.pitch() > 45f),
                    profile + " 放方块时未低头");
        }
    }

    @Test
    void combatModelsRotateMultipleTargets() {
        for (BehaviorProfile profile : List.of(BehaviorProfile.TARGET_SWITCH, BehaviorProfile.MULTI_ENTITY)) {
            Set<String> targets = registry.of(profile).generate("legit-0", PLATFORM, 0L, new Random(5L), 160)
                    .stream()
                    .filter(Event.Combat.class::isInstance)
                    .map(Event.Combat.class::cast)
                    .map(Event.Combat::targetId)
                    .collect(Collectors.toSet());
            assertTrue(targets.size() >= 2, profile + " 目标未轮换: " + targets);
        }
    }

    @Test
    void inventoryModelMovesItemsWithinStackSize() {
        List<Event.Inventory> moves = registry.of(BehaviorProfile.INVENTORY_SORT)
                .generate("legit-0", PLATFORM, 0L, new Random(13L), 60).stream()
                .filter(Event.Inventory.class::isInstance)
                .map(Event.Inventory.class::cast)
                .toList();
        assertFalse(moves.isEmpty(), "未产出背包事件");
        for (Event.Inventory move : moves) {
            assertTrue(move.amount() >= 1 && move.amount() <= 64, "堆叠数越界: " + move.amount());
            assertFalse(move.itemId().isBlank());
        }
    }

    @Test
    void mlgAndPearlEmitInteractionSequences() {
        List<Event> mlg = registry.of(BehaviorProfile.MLG).generate("legit-0", PLATFORM, 0L,
                new Random(17L), 80);
        assertTrue(mlg.stream().anyMatch(event -> event instanceof Event.Interact interact
                && "water".equals(interact.blockType())), "MLG 未放水桶");

        List<Event> pearl = registry.of(BehaviorProfile.PEARL).generate("legit-0", PLATFORM, 0L,
                new Random(19L), 80);
        assertTrue(pearl.stream().anyMatch(event -> event instanceof Event.Interact interact
                && "ender_pearl".equals(interact.blockType())), "珍珠未投掷");
    }

    @Test
    void unknownProfileIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> registry.of(null));
    }
}
