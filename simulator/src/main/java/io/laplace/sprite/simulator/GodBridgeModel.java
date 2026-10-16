package io.laplace.sprite.simulator;

import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.BehaviorProfile;
import io.laplace.sprite.core.model.PlatformKey;

import java.time.Instant;
import java.util.List;
import java.util.Random;

/** 上帝桥：潜行贴边后退搭桥，每 2 tick 放一块。 */
public final class GodBridgeModel extends MovementModel {

    private static final int PLACE_INTERVAL = 2;

    public GodBridgeModel() {
        super(BehaviorProfile.GODBRIDGE);
    }

    @Override
    protected double speed() {
        return PhysicsPredictor.SNEAK_GROUND_SPEED;
    }

    @Override
    protected boolean sneaking() {
        return true;
    }

    /** 每 2 tick 往脚下放一块，必须低头看放置面。 */
    @Override
    protected float viewPitch() {
        return 75f;
    }

    @Override
    protected void decorate(String playerId, long tick, Instant timestamp, PlatformKey platform,
                            Position position, Random rng, List<Event> out) {
        if (tick % PLACE_INTERVAL == 0) {
            placeBlock(playerId, tick, timestamp, platform, position, "stone", out);
        }
    }
}
