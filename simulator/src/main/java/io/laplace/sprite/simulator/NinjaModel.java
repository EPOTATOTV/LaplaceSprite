package io.laplace.sprite.simulator;

import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.BehaviorProfile;
import io.laplace.sprite.core.model.PlatformKey;

import java.time.Instant;
import java.util.List;
import java.util.Random;

/** 忍者桥：疾跑连续搭桥，每 tick 放一块，速度贴近普通搭桥上限。 */
public final class NinjaModel extends MovementModel {

    public NinjaModel() {
        super(BehaviorProfile.NINJA);
    }

    @Override
    protected double speed() {
        return PhysicsPredictor.SPRINT_GROUND_SPEED;
    }

    @Override
    protected boolean sprinting() {
        return true;
    }

    /** 每 tick 把方块放到脚下，必须低头把准星压在放置面上。 */
    @Override
    protected float viewPitch() {
        return 78f;
    }

    @Override
    protected void decorate(String playerId, long tick, Instant timestamp, PlatformKey platform,
                            Position position, Random rng, List<Event> out) {
        placeBlock(playerId, tick, timestamp, platform, position, "stone", out);
    }
}
