package io.laplace.sprite.simulator;

import io.laplace.sprite.core.model.BehaviorProfile;

/** 游泳：水中 0.8 水平阻力，终端速度约 2 格/秒，全程离地。 */
public final class SwimModel extends MovementModel {

    public SwimModel() {
        super(BehaviorProfile.SWIM);
    }

    @Override
    protected double speed() {
        return PhysicsPredictor.SWIM_SPEED;
    }

    @Override
    protected MotionMode motionMode() {
        return MotionMode.SWIM;
    }
}
