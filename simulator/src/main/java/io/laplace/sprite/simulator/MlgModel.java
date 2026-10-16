package io.laplace.sprite.simulator;

import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.BehaviorProfile;
import io.laplace.sprite.core.model.PlatformKey;

import java.time.Instant;
import java.util.List;
import java.util.Random;

/**
 * MLG 落地接水：从 20 格高处按原版重力下坠，落地帧放水桶，随后收回。
 * 20 格的自由落体约 24 tick 结束，正好落在放水的相位上。
 */
public final class MlgModel extends MovementModel {

    private static final double START_Y = 78.0;
    private static final double GROUND_Y = 58.0;
    private static final int CYCLE_TICKS = 40;
    private static final int LAND_PHASE = 25;
    private static final int RECOVER_PHASE = 32;

    public MlgModel() {
        super(BehaviorProfile.MLG);
    }

    @Override
    protected double startY() {
        return START_Y;
    }

    @Override
    protected double groundY() {
        return GROUND_Y;
    }

    @Override
    protected double speed() {
        return 0.08;
    }

    @Override
    protected void decorate(String playerId, long tick, Instant timestamp, PlatformKey platform,
                            Position position, Random rng, List<Event> out) {
        int phase = (int) (tick % CYCLE_TICKS);
        if (phase == LAND_PHASE) {
            placeBlock(playerId, tick, timestamp, platform, position, "water", out);
        } else if (phase == RECOVER_PHASE) {
            out.add(new Event.BlockChange(playerId, tick, timestamp, platform, position.blockX(),
                    position.blockY() - 1, position.blockZ(), "water", "air"));
        }
    }
}
