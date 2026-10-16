package io.laplace.sprite.simulator;

import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.BehaviorProfile;
import io.laplace.sprite.core.model.PlatformKey;

import java.time.Instant;
import java.util.List;
import java.util.Random;

/** 多目标：同一 tick 对 2 个以上实体出手，目标每 20 tick 轮换。 */
public final class MultiEntityModel extends MovementModel {

    private static final String[] TARGETS = {"mob-1", "mob-2", "mob-3", "mob-4"};
    private static final int ROTATE_TICKS = 20;
    /** 攻击距离上限 3.0 格（原版生存模式），留 0.3 格余量。 */
    private static final double REACH_BASE = 2.7;
    private static final double REACH_SPAN = 0.3;

    public MultiEntityModel() {
        super(BehaviorProfile.MULTI_ENTITY);
    }

    @Override
    protected double speed() {
        return 0.06;
    }

    @Override
    protected void decorate(String playerId, long tick, Instant timestamp, PlatformKey platform,
                            Position position, Random rng, List<Event> out) {
        int offset = (int) (tick / ROTATE_TICKS);
        int clicksPerSecond = 8 + rng.nextInt(5);
        for (int i = 0; i < 2; i++) {
            String target = TARGETS[(offset + i) % TARGETS.length];
            out.add(new Event.Combat(playerId, tick, timestamp, platform, target,
                    REACH_BASE + rng.nextDouble() * REACH_SPAN, clicksPerSecond, "butterfly"));
        }
    }
}
