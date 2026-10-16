package io.laplace.sprite.simulator;

import java.util.Random;

/**
 * 亚容差噪声：叠在物理预测结果上的扰动，幅度严格低于检测引擎的阈值，
 * 保证「合法玩家 0 误报」不变式不被打破。每个预算常量都标注它对应的引擎阈值。
 */
final class SubToleranceNoise {

    /**
     * 单 tick 位置噪声幅值（格）。引擎 {@code sim.speed} 的落地超速阈值是 0.30 格/tick，
     * 合法包络上界为疾跑终端速度 {@link PhysicsPredictor#SPRINT_GROUND_SPEED} ≈ 0.2863；
     * 相邻两 tick 的噪声在位移上最多叠加 2 × 0.004 = 0.008，合计 0.2943，仍低于 0.30。
     * 引擎 {@code sim.blink} 的单 tick 突跳阈值是 1.4 格，噪声差了三个数量级，不可能触发。
     */
    static final double POSITION_BUDGET = 0.004;

    /**
     * 单 tick 朝向噪声幅值（度）。引擎未对转角设阈值，但模拟器自身把合法单 tick 转角限定在 6.0°
     * （人类颈部转速量级，也是行为模型平滑性断言的界）；基础转向限幅 4.5° 加本噪声 1.5°
     * 后恰好不超过 6.0°，不会出现人类做不到的转头速度。
     */
    static final double TURN_BUDGET = 1.5;

    /**
     * 点击间隔抖动下限（tick 标准差）。引擎 {@code sim.auto-clicker} 先要求全部样本 CPS ≥ 12，
     * 再要求连击间隔标准差 < 0.5；人类点击间隔标准差在 1 tick 以上，取下限 1.0 可恒不落入脚本判定。
     */
    static final double CLICK_INTERVAL_JITTER = 1.0;

    /** 人类按键占空比：绝大多数 tick 满输入，少数 tick 短暂松键。 */
    private static final double DUTY_FULL_PROBABILITY = 0.96;
    private static final double DUTY_MIN = 0.85;
    private static final double DUTY_MAX = 0.95;

    private SubToleranceNoise() {
    }

    /** 单 tick 位置偏移，均匀落在 ±POSITION_BUDGET 内。 */
    static double position(Random rng) {
        return (rng.nextDouble() * 2 - 1) * POSITION_BUDGET;
    }

    /** 单 tick 朝向抖动（度），均匀落在 ±TURN_BUDGET 内。 */
    static double turn(Random rng) {
        return (rng.nextDouble() * 2 - 1) * TURN_BUDGET;
    }

    /** 本 tick 的按键占空比，只减不增，因此位移包络上界仍是原版终端速度。 */
    static double inputDuty(Random rng) {
        if (rng.nextDouble() < DUTY_FULL_PROBABILITY) {
            return 1.0;
        }
        return DUTY_MIN + rng.nextDouble() * (DUTY_MAX - DUTY_MIN);
    }

    /**
     * 人类点击间隔（tick）：均值 averageTicks、标准差 CLICK_INTERVAL_JITTER，最小 2 tick。
     * 下限 2 tick 对应 CPS 上限 10，落在引擎认可的 1–12 CPS 内。
     */
    static int clickInterval(Random rng, int averageTicks) {
        double jittered = averageTicks + rng.nextGaussian() * CLICK_INTERVAL_JITTER;
        return Math.max(2, (int) Math.round(jittered));
    }
}
