package io.laplace.sprite.simulator;

import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.BehaviorProfile;
import io.laplace.sprite.core.model.PlatformKey;

import java.time.Instant;
import java.util.List;
import java.util.Random;

/** 末影珍珠推进：每 40 tick 投掷一次，投掷帧给玩家一个沿视线的水平冲量。 */
public final class PearlModel extends MovementModel {

    private static final int CYCLE_TICKS = 40;
    private static final int THROW_PHASE = 20;
    /** 珍珠推进的冲量量级：一 tick 内 0.4 格，远低于引擎 1.4 格的位置突跳阈值。 */
    private static final double DASH_IMPULSE = 0.4;

    public PearlModel() {
        super(BehaviorProfile.PEARL);
    }

    @Override
    protected double speed() {
        return 0.15;
    }

    @Override
    protected double horizontalImpulse(int tick) {
        return tick % CYCLE_TICKS == THROW_PHASE ? DASH_IMPULSE : 0.0;
    }

    @Override
    protected void decorate(String playerId, long tick, Instant timestamp, PlatformKey platform,
                            Position position, Random rng, List<Event> out) {
        if (tick % CYCLE_TICKS != THROW_PHASE) {
            return;
        }
        out.add(new Event.Interact(playerId, tick, timestamp, platform, "ender_pearl",
                position.blockX(), position.blockY(), position.blockZ(), "use"));
        out.add(new Event.Packet(playerId, tick, timestamp, platform, "client->server", "use_item", 24));
    }
}
