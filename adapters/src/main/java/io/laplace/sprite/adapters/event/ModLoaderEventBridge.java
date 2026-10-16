package io.laplace.sprite.adapters.event;

import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.spi.EventBridge;

/**
 * 模组加载器事件桥（14.6）：Forge / NeoForge / Fabric / Quilt。
 *
 * <p>除公共事件外，额外产出注册表快照（REGISTRY）、GameTest 套件结果（GAME_TEST_SUITE）、
 * tick 指标（TICK）与模组哈希（MOD_HASH）。</p>
 */
public final class ModLoaderEventBridge implements EventBridge {

    private final PlatformFamily family;

    public ModLoaderEventBridge(PlatformFamily family) {
        this.family = family;
    }

    public PlatformFamily family() {
        return family;
    }

    @Override
    public EventStream bridge(PlatformKey key) {
        return SyntheticStreams.modloader(SyntheticStreams.PLAYER, key);
    }
}
