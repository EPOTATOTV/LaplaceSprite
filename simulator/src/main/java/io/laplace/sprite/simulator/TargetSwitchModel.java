package io.laplace.sprite.simulator;

import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.BehaviorProfile;
import io.laplace.sprite.core.model.PlatformKey;

import java.time.Instant;
import java.util.List;
import java.util.Random;

/** 目标切换：在 3 个目标之间每 15 tick 轮换，保持 6-10 CPS。 */
public final class TargetSwitchModel extends MovementModel {

    private static final String[] TARGETS = {"target-a", "target-b", "target-c"};
    private static final int SWITCH_TICKS = 15;

    public TargetSwitchModel() {
        super(BehaviorProfile.TARGET_SWITCH);
    }

    @Override
    protected double speed() {
        return 0.08;
    }

    @Override
    protected void decorate(String playerId, long tick, Instant timestamp, PlatformKey platform,
                            Position position, Random rng, List<Event> out) {
        String target = TARGETS[(int) ((tick / SWITCH_TICKS) % TARGETS.length)];
        out.add(new Event.Combat(playerId, tick, timestamp, platform, target, 3.0,
                6 + rng.nextInt(5), "single"));
    }
}
