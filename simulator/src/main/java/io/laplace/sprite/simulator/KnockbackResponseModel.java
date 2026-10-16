package io.laplace.sprite.simulator;

import io.laplace.sprite.core.model.BehaviorProfile;

/**
 * 击退响应：受击帧获得原版击退冲量（水平 1.2 格/tick，竖直分量把玩家抬离地面），
 * 滞空期间几乎无法输入，落地后水平速度被地面摩擦迅速吃掉，再恢复到巡航意图。
 */
public final class KnockbackResponseModel extends MovementModel {

    private static final int CYCLE_TICKS = 30;
    private static final int RECOVER_TICKS = 8;
    private static final double CRUISE_SPEED = 0.06;
    private static final double KNOCKBACK_IMPULSE = 1.2;
    private static final double KNOCKBACK_LIFT = 0.36;

    public KnockbackResponseModel() {
        super(BehaviorProfile.KNOCKBACK_RESPONSE);
    }

    @Override
    protected double speed() {
        return CRUISE_SPEED;
    }

    /** 受击后的恢复期内玩家基本没有移动输入。 */
    @Override
    protected double speed(int tick) {
        return tick % CYCLE_TICKS < RECOVER_TICKS ? 0.0 : CRUISE_SPEED;
    }

    @Override
    protected double horizontalImpulse(int tick) {
        return tick % CYCLE_TICKS == 0 ? KNOCKBACK_IMPULSE : 0.0;
    }

    @Override
    protected double jumpImpulse(int tick) {
        return tick % CYCLE_TICKS == 0 ? KNOCKBACK_LIFT : 0.0;
    }
}
