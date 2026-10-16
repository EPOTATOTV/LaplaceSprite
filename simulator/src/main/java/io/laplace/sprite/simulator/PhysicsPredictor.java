package io.laplace.sprite.simulator;

import java.util.Random;

/**
 * 原版 Minecraft 运动预测器（4.1 轨迹生成器）：按 MC 物理规则逐 tick 预测玩家应到的位置，
 * 作为合法轨迹的 ground truth。行为模型只提供意图（想走多快、是否疾跑/潜行/起跳），
 * 逐 tick 位移由本类按原版摩擦、阻力与重力算出。
 *
 * <p>常量与推导（20 TPS，长度单位格、速度单位格/tick）：</p>
 * <ul>
 *   <li>移动属性 {@link #MOVEMENT_SPEED} = 0.1 是地面每 tick 的输入加速度：原版
 *        {@code Player.getFrictionInfluencedSpeed} 取 {@code movementSpeed × 0.216 / 滑度³}，
 *       滑度默认 0.6 时 0.216/0.216 = 1，正好折回属性值本身。</li>
 *   <li>地面水平摩擦 = 滑度 × 空气阻力 = {@link #GROUND_FRICTION} = 0.6 × 0.91 = 0.546；
 *       空中水平阻力 {@link #AIR_DRAG} = 0.91。</li>
 *   <li>地面终端速度 = 输入加速度 / (1 − 摩擦)：步行 0.1/0.454 ≈ 0.2203、疾跑 0.13/0.454 ≈ 0.2863、
 *       潜行 0.03/0.454 ≈ 0.0661。原版实测为 4.317 / 5.612 / 1.3 格每秒（0.2158 / 0.2806 / 0.065），
 *       本式未计入原版对速度分量的逐 tick 阻尼，比实测高约 2%，量级一致。</li>
 *   <li>重力 {@link #GRAVITY} = 0.08 格/tick²，竖直速度逐 tick 乘 {@link #VERTICAL_DRAG} = 0.98，
 *       终端下落速度 0.08 × 0.98 / (1 − 0.98) = 3.92 格/tick。</li>
 *   <li>起跳竖直初速 {@link #JUMP_VELOCITY} = 0.42 格/tick；空中水平输入加速度 0.02
 *       （原版 {@code flyingSpeed}），远小于地面输入。</li>
 * </ul>
 *
 * <p>确定性：本类不调用 {@link Math#random()}，唯一随机来源是调用方传入的 {@link Random}，
 * 且仅用于人类操作节奏的按键占空比抖动，占空比只减不增，因此不会把位移抬到原版包络之上。</p>
 *
 * <p>地形简化为一层水平地面：不做方块碰撞与流体判定，因此原版 0.6 格自动上台阶在平面地上
 * 退化为贴地（不产生竖直位移），落地即贴到 {@link PredictionState#groundY()}。</p>
 */
final class PhysicsPredictor {

    /** 原版玩家 movementSpeed 属性默认值：地面每 tick 的输入加速度，先于摩擦与疾跑/潜行倍率。 */
    static final double MOVEMENT_SPEED = 0.1;
    /** 疾跑把移动输入乘 1.3。 */
    static final double SPRINT_MULTIPLIER = 1.3;
    /** 潜行把移动输入乘 0.3。 */
    static final double SNEAK_MULTIPLIER = 0.3;
    /** 疾跑的地面输入加速度 = 移动属性 × 1.3。 */
    static final double SPRINT_INPUT = MOVEMENT_SPEED * SPRINT_MULTIPLIER;
    /** 潜行的地面输入加速度 = 移动属性 × 0.3。 */
    static final double SNEAK_INPUT = MOVEMENT_SPEED * SNEAK_MULTIPLIER;

    /** 默认方块滑度。 */
    static final double GROUND_SLIPPERINESS = 0.6;
    /** 水平空气阻力。 */
    static final double AIR_DRAG = 0.91;
    /** 地面水平摩擦 = 滑度 × 空气阻力。 */
    static final double GROUND_FRICTION = GROUND_SLIPPERINESS * AIR_DRAG;

    /** 重力加速度（格/tick²）。 */
    static final double GRAVITY = 0.08;
    /** 竖直速度逐 tick 阻尼。 */
    static final double VERTICAL_DRAG = 0.98;
    /** 终端下落速度。 */
    static final double TERMINAL_FALL_SPEED = GRAVITY * VERTICAL_DRAG / (1.0 - VERTICAL_DRAG);
    /** 起跳竖直初速。 */
    static final double JUMP_VELOCITY = 0.42;
    /** 空中水平输入加速度（原版 flyingSpeed）。 */
    static final double AIR_INPUT = 0.02;

    /** 水中水平阻力。 */
    static final double WATER_DRAG = 0.8;
    /** 水中推进输入，取原版 flyingSpeed 量级；终端速度 0.02/(1−0.8) = 0.1 格/tick ≈ 2 格/秒。 */
    static final double SWIM_INPUT = AIR_INPUT;
    /** 载具（船/坐骑）水平阻力。 */
    static final double VEHICLE_DRAG = 0.9;

    /** 鞘翅竖直阻尼。 */
    static final double ELYTRA_DRAG = 0.99;
    /** 鞘翅平飞的基础下沉量，按原版 0.2 格/秒 ≈ 0.01 格/tick。 */
    static final double GLIDE_SINK = 0.01;
    /** 鞘翅俯仰升力的角度截断，避免接近垂直时 tan 发散。 */
    static final double GLIDE_PITCH_LIMIT = 45.0;

    /** 步行地面终端速度。 */
    static final double WALK_GROUND_SPEED = terminalGroundSpeed(MOVEMENT_SPEED);
    /** 疾跑地面终端速度，也是本模拟合法轨迹的水平位移包络上界。 */
    static final double SPRINT_GROUND_SPEED = terminalGroundSpeed(SPRINT_INPUT);
    /** 潜行地面终端速度。 */
    static final double SNEAK_GROUND_SPEED = terminalGroundSpeed(SNEAK_INPUT);
    /** 水中终端速度。 */
    static final double SWIM_SPEED = SWIM_INPUT / (1.0 - WATER_DRAG);

    private PhysicsPredictor() {
    }

    /** 地面终端速度 = 输入加速度 / (1 − 地面摩擦)。 */
    static double terminalGroundSpeed(double input) {
        return input / (1.0 - GROUND_FRICTION);
    }

    /** 地面输入加速度：按疾跑/潜行倍率取档。 */
    static double groundInput(boolean sprinting, boolean sneaking) {
        double input = MOVEMENT_SPEED;
        if (sprinting) {
            input *= SPRINT_MULTIPLIER;
        }
        if (sneaking) {
            input *= SNEAK_MULTIPLIER;
        }
        return input;
    }

    /**
     * 逐 tick 预测下一个 tick 的状态。
     *
     * <p>顺序与原版一致：先施加冲量（击退、珍珠推进、起跳），再按介质摩擦衰减速度，
     * 最后叠加本 tick 的输入加速度并按速度位移。已经离地的 tick 不吃地面摩擦，
     * 避免原版击退在起跳那一帧就被落地摩擦抹掉。</p>
     */
    static PredictionState predict(PredictionState state, MovementInput input, Random rng) {
        double heading = Math.toRadians(state.yaw());
        double dirX = Math.sin(heading);
        double dirZ = Math.cos(heading);
        double vx = state.vx();
        double vy = state.vy();
        double vz = state.vz();

        // 冲量沿视线施加：原版击退与末影珍珠都是瞬时给速度，不进摩擦累积
        if (input.horizontalImpulse() != 0.0) {
            vx += dirX * input.horizontalImpulse();
            vz += dirZ * input.horizontalImpulse();
        }
        // 起跳冲量只在落地时生效：原版空中按键不会起跳
        if (input.jumpImpulse() > 0.0 && state.onGround()) {
            vy = Math.max(vy, 0.0) + input.jumpImpulse();
        }
        // 冲量帧按空中处理：瞬时冲量刚把玩家推出，本 tick 不该再吃落地摩擦
        boolean grounded = state.onGround() && vy <= 0.0 && input.horizontalImpulse() == 0.0;

        double duty = SubToleranceNoise.inputDuty(rng);
        double nextY = state.y();
        boolean nextGround = false;

        switch (input.mode()) {
            case GROUND -> {
                double friction = grounded ? GROUND_FRICTION : AIR_DRAG;
                double base = grounded ? groundInput(input.sprinting(), input.sneaking()) : AIR_INPUT;
                double terminal = base / (1.0 - friction);
                vx = vx * friction + dirX * inputAccel(base, terminal, input.desiredSpeed()) * duty;
                vz = vz * friction + dirZ * inputAccel(base, terminal, input.desiredSpeed()) * duty;
                // 竖直方向原版先按当前竖直速度位移、再加竖直阻尼与重力，所以 0.42 的起跳初速
                // 第一帧就完整走满，跳跃峰高 1.2522 格（0.42+0.3332+0.2481+0.1648+0.0831+0.0030）
                nextY = state.y() + vy;
                if (nextY <= state.groundY()) {
                    nextY = state.groundY();
                    vy = 0.0;
                    nextGround = true;
                } else {
                    // 终端速度截断：原版下落速度上限就是 0.08×0.98/(1−0.98)
                    vy = Math.max((vy - GRAVITY) * VERTICAL_DRAG, -TERMINAL_FALL_SPEED);
                }
            }
            case SWIM -> {
                vx = vx * WATER_DRAG + dirX * inputAccel(SWIM_INPUT, SWIM_SPEED, input.desiredSpeed()) * duty;
                vz = vz * WATER_DRAG + dirZ * inputAccel(SWIM_INPUT, SWIM_SPEED, input.desiredSpeed()) * duty;
                // 浮力抵消大部分重力，竖直速度阻尼归零，水面附近中性漂浮
                vy *= WATER_DRAG;
                nextY = state.y() + vy;
            }
            case ELYTRA -> {
                double horizon = Math.hypot(vx, vz);
                double accel = input.desiredSpeed() * (1.0 - ELYTRA_DRAG) * duty;
                if (horizon < input.desiredSpeed()) {
                    vx = vx * ELYTRA_DRAG + dirX * accel;
                    vz = vz * ELYTRA_DRAG + dirZ * accel;
                } else {
                    vx *= ELYTRA_DRAG;
                    vz *= ELYTRA_DRAG;
                }
                // 竖直：0.99 阻尼 + 重力 0.08，俯仰升力 -tan(pitch)×水平速度 抵消部分重力。
                // 升力以重力为上限（滑翔不建模火箭推进），因此净下沉恒为非负，不会持续爬升。
                double lift = Math.min(GRAVITY, -Math.tan(Math.toRadians(glidePitch(state.pitch())))
                        * Math.hypot(vx, vz));
                double targetVy = -(GRAVITY - lift + GLIDE_SINK);
                vy = vy * ELYTRA_DRAG + targetVy * (1.0 - ELYTRA_DRAG);
                nextY = state.y() + vy;
            }
            case VEHICLE -> {
                double accel = input.desiredSpeed() * (1.0 - VEHICLE_DRAG) * duty;
                if (Math.hypot(vx, vz) < input.desiredSpeed()) {
                    vx = vx * VEHICLE_DRAG + dirX * accel;
                    vz = vz * VEHICLE_DRAG + dirZ * accel;
                } else {
                    vx *= VEHICLE_DRAG;
                    vz *= VEHICLE_DRAG;
                }
                // 载具贴水面行进，不做重力
                vy = 0.0;
            }
        }
        return new PredictionState(state.x() + vx, nextY, state.z() + vz, state.yaw(), state.pitch(),
                vx, vy, vz, state.groundY(), nextGround);
    }

    /** 把期望速度折算成输入加速度：意图不超过终端速度时按比例给输入，超过时按满输入。 */
    private static double inputAccel(double base, double terminal, double desiredSpeed) {
        if (terminal <= 0.0) {
            return 0.0;
        }
        return base * Math.min(1.0, Math.max(0.0, desiredSpeed / terminal));
    }

    private static double glidePitch(float pitch) {
        return Math.max(-GLIDE_PITCH_LIMIT, Math.min(GLIDE_PITCH_LIMIT, pitch));
    }
}
