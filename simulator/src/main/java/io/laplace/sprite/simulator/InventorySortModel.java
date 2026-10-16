package io.laplace.sprite.simulator;

import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.BehaviorProfile;
import io.laplace.sprite.core.model.PlatformKey;

import java.time.Instant;
import java.util.List;
import java.util.Random;

/** 背包整理：原地不动，2 tick 一次槽位移动。 */
public final class InventorySortModel extends MovementModel {

    private static final String[] SLOTS = {"hotbar.0", "hotbar.1", "hotbar.8", "inventory.9",
            "inventory.18", "inventory.27"};
    private static final String[] ITEMS = {"diamond_sword", "golden_apple", "ender_pearl",
            "cobblestone", "water_bucket", "obsidian"};

    public InventorySortModel() {
        super(BehaviorProfile.INVENTORY_SORT);
    }

    @Override
    protected double speed() {
        return 0.0;
    }

    @Override
    protected void decorate(String playerId, long tick, Instant timestamp, PlatformKey platform,
                            Position position, Random rng, List<Event> out) {
        if (tick % 2 != 0) {
            return;
        }
        out.add(new Event.Inventory(playerId, tick, timestamp, platform,
                SLOTS[rng.nextInt(SLOTS.length)], ITEMS[rng.nextInt(ITEMS.length)],
                1 + rng.nextInt(64)));
    }
}
