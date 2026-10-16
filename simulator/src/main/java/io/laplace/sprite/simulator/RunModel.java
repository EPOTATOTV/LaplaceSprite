package io.laplace.sprite.simulator;

import io.laplace.sprite.core.model.BehaviorProfile;

/**
 * 奔跑：原版没有独立的"奔跑"档，不按疾跑键时无论怎么走都是步行终端速度，
 * 因此本模型与步行同档，两者只在行为语义上区分，不再人为加快。
 */
public final class RunModel extends MovementModel {

    public RunModel() {
        super(BehaviorProfile.RUN);
    }

    @Override
    protected double speed() {
        return PhysicsPredictor.WALK_GROUND_SPEED;
    }
}
