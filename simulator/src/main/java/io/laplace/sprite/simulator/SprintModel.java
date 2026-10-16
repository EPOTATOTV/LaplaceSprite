package io.laplace.sprite.simulator;

import io.laplace.sprite.core.model.BehaviorProfile;

/** 疾跑：移动输入乘 1.3，原版地面终端速度约 5.7 格/秒，sprinting 标记为真。 */
public final class SprintModel extends MovementModel {

    public SprintModel() {
        super(BehaviorProfile.SPRINT);
    }

    @Override
    protected double speed() {
        return PhysicsPredictor.SPRINT_GROUND_SPEED;
    }

    @Override
    protected boolean sprinting() {
        return true;
    }
}
