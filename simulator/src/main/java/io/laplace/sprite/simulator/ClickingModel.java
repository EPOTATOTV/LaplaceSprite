package io.laplace.sprite.simulator;

import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.BehaviorProfile;
import io.laplace.sprite.core.model.PlatformKey;

import java.time.Instant;
import java.util.List;
import java.util.Random;

/**
 * 点击节奏：不必每 tick 出手，按人类节奏连点，间隔取带抖动的样本，
 * 上报 CPS 与实际间隔一致，因此 CPS 落在 1–12、间隔标准差也在人类范围。
 */
public final class ClickingModel extends MovementModel {

    private static final String[] SWING_TYPES = {"single", "butterfly", "drag"};
    private static final String TARGET = "target-click";
    /** 每个 tick 出手的概率，量级对应十点几次每秒的持续点击。 */
    private static final double CLICK_PROBABILITY = 0.5;
    /** 点击间隔中枢（tick）。 */
    private static final int AVERAGE_INTERVAL_TICKS = 2;
    private static final double DOUBLE_CLICK_PROBABILITY = 0.06;

    public ClickingModel() {
        super(BehaviorProfile.CLICKING);
    }

    @Override
    protected double speed() {
        return 0.06;
    }

    @Override
    protected void decorate(String playerId, long tick, Instant timestamp, PlatformKey platform,
                            Position position, Random rng, List<Event> out) {
        if (rng.nextDouble() >= CLICK_PROBABILITY) {
            return;
        }
        String swing = SWING_TYPES[rng.nextInt(SWING_TYPES.length)];
        int interval = SubToleranceNoise.clickInterval(rng, AVERAGE_INTERVAL_TICKS);
        int clicksPerSecond = (int) Math.round(20.0 / interval);
        double reach = 2.6 + rng.nextDouble() * 0.4;
        out.add(new Event.Combat(playerId, tick, timestamp, platform, TARGET, reach, clicksPerSecond, swing));
        if (rng.nextDouble() < DOUBLE_CLICK_PROBABILITY) {
            out.add(new Event.Combat(playerId, tick, timestamp, platform, TARGET, reach, clicksPerSecond, swing));
        }
    }
}
