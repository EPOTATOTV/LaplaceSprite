package io.laplace.sprite.simulator;

import io.laplace.sprite.core.model.BehaviorProfile;

/** 鞘翅滑翔：水平约 12 格/秒，竖直受俯仰升力控制缓慢下沉，全程离地。 */
public final class ElytraModel extends MovementModel {

    private static final double BLOCKS_PER_SECOND = 12.0;

    public ElytraModel() {
        super(BehaviorProfile.ELYTRA);
    }

    @Override
    protected double speed() {
        return BLOCKS_PER_SECOND / 20.0;
    }

    @Override
    protected MotionMode motionMode() {
        return MotionMode.ELYTRA;
    }
}
