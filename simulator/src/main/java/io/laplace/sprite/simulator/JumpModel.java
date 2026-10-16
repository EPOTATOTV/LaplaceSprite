package io.laplace.sprite.simulator;

import io.laplace.sprite.core.model.BehaviorProfile;

/**
 * 跳跃前进：疾跑连跳，每 12 tick 按一次跳跃键。
 * 原版一次跳跃的滞空时间约 12 tick（上升 6、下降 6），因此每个周期都会落地一次；
 * 空中按下的跳跃键不生效，节奏被打乱时自然退化成等待下一次落地。
 */
public final class JumpModel extends MovementModel {

    private static final int CYCLE_TICKS = 12;

    public JumpModel() {
        super(BehaviorProfile.JUMP);
    }

    @Override
    protected double speed() {
        return PhysicsPredictor.SPRINT_GROUND_SPEED;
    }

    @Override
    protected boolean sprinting() {
        return true;
    }

    @Override
    protected double jumpImpulse(int tick) {
        return tick % CYCLE_TICKS == 0 ? PhysicsPredictor.JUMP_VELOCITY : 0.0;
    }
}
