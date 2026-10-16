package io.laplace.sprite.simulator;

import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.BehaviorProfile;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 物理预测器与亚容差噪声的包络断言。
 *
 * <p>simulator 不依赖 adapters 模块，因此引擎阈值以常量形式在此重复并标注来源：
 * 落地超速 0.30 格/tick（sim.speed）、单 tick 位移 1.4 格（sim.blink）、
 * 自动点击 CPS ≥ 12 且连击间隔标准差 &lt; 0.5（sim.auto-clicker）。</p>
 */
class PhysicsPredictorTest {

    private static final double ENGINE_GROUND_SPEED_LIMIT = 0.30;
    private static final double ENGINE_BLINK_DISTANCE = 1.4;
    private static final double ENGINE_CLICK_JITTER = 0.5;
    private static final int ENGINE_CLICK_MIN_CPS = 12;
    /** 模拟器自身与平滑性断言共同假定的单 tick 合法转角上限（度）。 */
    private static final double MAX_TURN_DEGREES = 6.0;
    private static final PlatformKey PLATFORM = PlatformKey.of(PlatformFamily.PAPER, "1.21");

    @Test
    void groundTerminalSpeedFollowsVanillaFrictionDerivation() {
        assertEquals(0.1, PhysicsPredictor.MOVEMENT_SPEED, 1e-9);
        assertEquals(0.546, PhysicsPredictor.GROUND_FRICTION, 1e-9);
        assertEquals(0.1 / (1 - 0.546), PhysicsPredictor.WALK_GROUND_SPEED, 1e-9);
        assertEquals(0.13 / (1 - 0.546), PhysicsPredictor.SPRINT_GROUND_SPEED, 1e-9);
        assertEquals(0.03 / (1 - 0.546), PhysicsPredictor.SNEAK_GROUND_SPEED, 1e-9);
        // 与原版实测的 4.317 / 5.612 / 1.3 格每秒同量级，偏差不超过 3%
        assertEquals(4.317 / 20.0, PhysicsPredictor.WALK_GROUND_SPEED, 0.006);
        assertEquals(5.612 / 20.0, PhysicsPredictor.SPRINT_GROUND_SPEED, 0.006);
        assertEquals(1.3 / 20.0, PhysicsPredictor.SNEAK_GROUND_SPEED, 0.004);
        assertTrue(PhysicsPredictor.SNEAK_GROUND_SPEED < PhysicsPredictor.WALK_GROUND_SPEED);
        assertTrue(PhysicsPredictor.WALK_GROUND_SPEED < PhysicsPredictor.SPRINT_GROUND_SPEED);
    }

    @Test
    void sprintEnvelopeSitsInsideTheEngineGroundSpeedLimit() {
        assertTrue(PhysicsPredictor.SPRINT_GROUND_SPEED < ENGINE_GROUND_SPEED_LIMIT);
        // 相邻两 tick 的位置噪声叠加后仍在阈值内，合法流不会被判超速
        assertTrue(PhysicsPredictor.SPRINT_GROUND_SPEED + 2 * SubToleranceNoise.POSITION_BUDGET
                < ENGINE_GROUND_SPEED_LIMIT);
        // 同时要高于阈值的 1/1.10，否则 SPEED 最小档（倍率 1.10）注入后仍不超限，边界样本会漏检
        assertTrue(PhysicsPredictor.SPRINT_GROUND_SPEED * 1.10 > ENGINE_GROUND_SPEED_LIMIT);
    }

    @Test
    void groundVelocityConvergesToTerminalSpeedWithoutOvershoot() {
        Random rng = new Random(20261013L);
        PredictionState state = new PredictionState(0, 64, 0, 90f, 0f, 0, 0, 0, 64, true);
        double maxStep = 0;
        double previousX = 0;
        for (int tick = 0; tick < 200; tick++) {
            state = PhysicsPredictor.predict(state, new MovementInput(MotionMode.GROUND,
                    PhysicsPredictor.SPRINT_GROUND_SPEED, true, false, 0, 0), rng);
            maxStep = Math.max(maxStep, state.x() - previousX);
            previousX = state.x();
            assertTrue(state.onGround(), "落地推进不应离地");
            assertEquals(64.0, state.y(), 0.0);
        }
        assertTrue(maxStep <= PhysicsPredictor.SPRINT_GROUND_SPEED, "位移越过原版包络: " + maxStep);
        assertTrue(maxStep > PhysicsPredictor.SPRINT_GROUND_SPEED * 0.95, "未收敛到终端速度: " + maxStep);
        assertTrue(maxStep < ENGINE_GROUND_SPEED_LIMIT);
    }

    @Test
    void jumpArcMatchesVanillaHeight() {
        Random rng = new Random(7L);
        PredictionState state = new PredictionState(0, 64, 0, 90f, 0f, 0, 0, 0, 64, true);
        double apex = 64;
        int airTicks = 0;
        for (int tick = 0; tick < 20; tick++) {
            double jump = tick == 0 ? PhysicsPredictor.JUMP_VELOCITY : 0.0;
            state = PhysicsPredictor.predict(state,
                    new MovementInput(MotionMode.GROUND, 0.0, false, false, jump, 0.0), rng);
            apex = Math.max(apex, state.y());
            if (!state.onGround()) {
                airTicks++;
            }
        }
        // 原版起跳逐 tick 抬升 0.42 / 0.3332 / 0.2481 / 0.1648 / 0.0831 / 0.0030，峰高 1.2522 格
        assertEquals(1.2522, apex - 64.0, 0.01);
        assertTrue(airTicks >= 9 && airTicks <= 13, "滞空 tick 数异常: " + airTicks);
    }

    @Test
    void everyProfileStaysWithinEngineDisplacementAndTurnBounds() {
        BehaviorModelRegistry registry = new BehaviorModelRegistry();
        for (BehaviorProfile profile : BehaviorProfile.values()) {
            List<Event.Move> moves = registry.of(profile)
                    .generate("legit-0", PLATFORM, 0L, new Random(97L), 400).stream()
                    .filter(Event.Move.class::isInstance)
                    .map(Event.Move.class::cast)
                    .toList();
            int groundRun = 0;
            int maxGroundRun = 0;
            Event.Move previous = null;
            for (Event.Move move : moves) {
                if (previous != null) {
                    double step = Math.hypot(move.x() - previous.x(), move.z() - previous.z());
                    assertTrue(step < ENGINE_BLINK_DISTANCE, profile + " 单 tick 位移越界: " + step);
                    double yawDelta = Math.abs(move.yaw() - previous.yaw());
                    double turn = Math.min(yawDelta, 360.0 - yawDelta);
                    assertTrue(turn <= MAX_TURN_DEGREES, profile + " 转向跳变: " + turn);
                    if (move.onGround() && step > ENGINE_GROUND_SPEED_LIMIT) {
                        groundRun++;
                        maxGroundRun = Math.max(maxGroundRun, groundRun);
                    } else {
                        groundRun = 0;
                    }
                }
                previous = move;
            }
            assertTrue(maxGroundRun < 8, profile + " 连续落地超速 tick 数: " + maxGroundRun);
        }
    }

    @Test
    void clickIntervalJitterKeepsCpsInsideHumanRange() {
        Random rng = new Random(5L);
        List<Integer> intervals = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            intervals.add(SubToleranceNoise.clickInterval(rng, 2));
        }
        for (int interval : intervals) {
            assertTrue(interval >= 2, "点击间隔过短: " + interval);
            int cps = (int) Math.round(20.0 / interval);
            assertTrue(cps >= 1 && cps <= ENGINE_CLICK_MIN_CPS, "CPS 越界: " + cps);
        }
        double mean = intervals.stream().mapToInt(Integer::intValue).average().orElse(0);
        double variance = intervals.stream()
                .mapToDouble(interval -> Math.pow(interval - mean, 2))
                .sum() / intervals.size();
        assertTrue(Math.sqrt(variance) >= ENGINE_CLICK_JITTER, "间隔抖动过低: " + Math.sqrt(variance));
    }
}
