package io.laplace.sprite.simulator;

import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.BehaviorProfile;
import io.laplace.sprite.core.model.PlatformKey;

import java.time.Instant;
import java.util.List;
import java.util.Random;

/** 挖掘：站在矿道里小步调整，每 4 tick 破坏一个方块并上报挖掘包。 */
public final class MineModel extends MovementModel {

    private static final String[] ORES = {"stone", "coal_ore", "iron_ore", "diamond_ore"};
    private static final int BREAK_INTERVAL = 4;

    public MineModel() {
        super(BehaviorProfile.MINE);
    }

    @Override
    protected double speed() {
        return 0.05;
    }

    @Override
    protected void decorate(String playerId, long tick, Instant timestamp, PlatformKey platform,
                            Position position, Random rng, List<Event> out) {
        if (tick % BREAK_INTERVAL != 0) {
            return;
        }
        int x = position.blockX();
        int y = position.blockY();
        int z = position.blockZ();
        String ore = ORES[rng.nextInt(ORES.length)];
        out.add(new Event.Interact(playerId, tick, timestamp, platform, ore, x, y, z, "break"));
        out.add(new Event.BlockChange(playerId, tick, timestamp, platform, x, y, z, ore, "air"));
        out.add(new Event.Packet(playerId, tick, timestamp, platform, "client->server",
                "player_digging", 16 + rng.nextInt(32)));
    }
}
