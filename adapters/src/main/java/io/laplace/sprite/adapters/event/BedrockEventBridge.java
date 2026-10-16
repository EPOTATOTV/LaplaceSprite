package io.laplace.sprite.adapters.event;

import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.spi.EventBridge;

/**
 * 基岩家族事件桥（14.6）：Nukkit / PowerNukkitX / Allay / Geyser。
 *
 * <p>基岩侧没有 Java 版的 brand + mod 哈希体系，用附加组件（ADDON）与 bedrock brand 表达身份；
 * PlayerJoin / Interact / Move 语义与 Java 版一致，直接复用公共事件集。</p>
 */
public final class BedrockEventBridge implements EventBridge {

    private final PlatformFamily family;

    public BedrockEventBridge(PlatformFamily family) {
        this.family = family;
    }

    public PlatformFamily family() {
        return family;
    }

    @Override
    public EventStream bridge(PlatformKey key) {
        return SyntheticStreams.bedrock(SyntheticStreams.PLAYER, key);
    }
}
