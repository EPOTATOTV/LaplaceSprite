package io.laplace.sprite.simulator;

import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.BehaviorProfile;
import io.laplace.sprite.core.model.PlatformKey;

import java.time.Instant;
import java.util.List;
import java.util.Random;

/** Telly 跳桥：疾跑连跳，落点前放一块，12 tick 一个周期（与原版跳跃滞空一致）。 */
public final class TellyModel extends MovementModel {

    private static final int CYCLE_TICKS = 12;

    public TellyModel() {
        super(BehaviorProfile.TELLY);
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

    /** 落点前补块，必须低头看放置面。 */
    @Override
    protected float viewPitch() {
        return 75f;
    }

    @Override
    protected void decorate(String playerId, long tick, Instant timestamp, PlatformKey platform,
                            Position position, Random rng, List<Event> out) {
        if (tick % CYCLE_TICKS == CYCLE_TICKS - 1) {
            placeBlock(playerId, tick, timestamp, platform, position, "obsidian", out);
        }
    }
}
