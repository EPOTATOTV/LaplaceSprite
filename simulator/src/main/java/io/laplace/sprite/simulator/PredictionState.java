package io.laplace.sprite.simulator;

/**
 * 玩家运动状态快照：位置、朝向、速度与地面接触，由 {@link PhysicsPredictor} 逐 tick 推进。
 *
 * @param groundY 该行为脚下的地面高度，玩家落到该高度即贴地并把竖直速度归零
 */
record PredictionState(double x, double y, double z, float yaw, float pitch,
                       double vx, double vy, double vz, double groundY, boolean onGround) {

    PredictionState withYawPitch(float newYaw, float newPitch) {
        return new PredictionState(x, y, z, newYaw, newPitch, vx, vy, vz, groundY, onGround);
    }
}
