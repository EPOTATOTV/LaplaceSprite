package io.laplace.sprite.simulator;

import io.laplace.sprite.core.model.BehaviorProfile;

/** 步行：原版地面终端速度约 4.4 格/秒，由输入加速度 0.1 与地面摩擦 0.546 推出。 */
public final class WalkModel extends MovementModel {

    public WalkModel() {
        super(BehaviorProfile.WALK);
    }

    @Override
    protected double speed() {
        return PhysicsPredictor.WALK_GROUND_SPEED;
    }
}
