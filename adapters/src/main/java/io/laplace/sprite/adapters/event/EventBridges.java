package io.laplace.sprite.adapters.event;

import io.laplace.sprite.adapters.hybrid.HybridEventBridge;
import io.laplace.sprite.adapters.hybrid.HybridHosts;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.spi.EventBridge;

/**
 * 家族到事件桥的分发（14.6）。适配器不再各写一份桥实现。
 */
public final class EventBridges {

    private EventBridges() {
    }

    public static EventBridge of(PlatformFamily family) {
        // Sponge 系也是 modloader，判定要在 isModLoader 之前。
        if (family.isSponge()) {
            return new SpongeEventBridge(family);
        }
        if (family.isHybrid()) {
            return new HybridEventBridge(HybridHosts.bukkitHost(), HybridHosts.loaderFor(family));
        }
        if (family.isModLoader()) {
            return new ModLoaderEventBridge(family);
        }
        return switch (family) {
            case FOLIA -> new FoliaEventBridge();
            case BUNGEECORD, WATERFALL -> new BungeeCordEventBridge(family);
            case VELOCITY -> new VelocityEventBridge();
            case NUKKIT, POWER_NUKKIT_X, ALLAY, GEYSER -> new BedrockEventBridge(family);
            default -> new BukkitEventBridge(family);
        };
    }
}
