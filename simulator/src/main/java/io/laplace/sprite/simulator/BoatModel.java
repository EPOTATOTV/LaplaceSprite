package io.laplace.sprite.simulator;

import io.laplace.sprite.core.model.BehaviorProfile;

/** 划船：水面全速约 8 格/秒，贴水面行进、不落地。 */
public final class BoatModel extends MovementModel {

    private static final double BLOCKS_PER_SECOND = 8.0;

    public BoatModel() {
        super(BehaviorProfile.BOAT);
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
