package io.laplace.sprite.simulator;

import io.laplace.sprite.core.model.BehaviorProfile;

/** 骑乘：马匹奔跑约 7 格/秒，速度由坐骑决定，玩家本身不落地。 */
public final class RideModel extends MovementModel {

    private static final double BLOCKS_PER_SECOND = 7.0;

    public RideModel() {
        super(BehaviorProfile.RIDE);
    }

    @Override
    protected double speed() {
        return BLOCKS_PER_SECOND / 20.0;
    }

    @Override
    protected MotionMode motionMode() {
        return MotionMode.VEHICLE;
    }
}
