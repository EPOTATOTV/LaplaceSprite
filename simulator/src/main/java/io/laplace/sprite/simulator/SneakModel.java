package io.laplace.sprite.simulator;

import io.laplace.sprite.core.model.BehaviorProfile;

/** 潜行：移动输入乘 0.3，原版地面终端速度约 1.3 格/秒，sneaking 标记为真。 */
public final class SneakModel extends MovementModel {

    public SneakModel() {
        super(BehaviorProfile.SNEAK);
    }

    @Override
    protected double speed() {
        return PhysicsPredictor.SNEAK_GROUND_SPEED;
    }

    @Override
    protected boolean sneaking() {
        return true;
    }
}
