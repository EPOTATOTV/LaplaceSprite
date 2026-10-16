package io.laplace.sprite.simulator;

import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.BehaviorProfile;
import io.laplace.sprite.core.model.PlatformKey;

import java.time.Instant;
import java.util.List;
import java.util.Random;

/** 放置方块：小步挪动，每 3 tick 放一块。 */
public final class PlaceModel extends MovementModel {

    private static final String[] BLOCKS = {"oak_planks", "cobblestone", "sandstone"};
    private static final int PLACE_INTERVAL = 3;

    public PlaceModel() {
        super(BehaviorProfile.PLACE);
    }

    @Override
    protected double speed() {
        return 0.05;
    }

    /** 往脚下放方块，必须低头看放置面。 */
    @Override
    protected float viewPitch() {
        return 75f;
    }

    @Override
    protected void decorate(String playerId, long tick, Instant timestamp, PlatformKey platform,
                            Position position, Random rng, List<Event> out) {
        if (tick % PLACE_INTERVAL == 0) {
            placeBlock(playerId, tick, timestamp, platform, position,
                    BLOCKS[rng.nextInt(BLOCKS.length)], out);
        }
    }
}
