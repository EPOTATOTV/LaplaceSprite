package io.laplace.sprite.adapters.event;

import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.event.EventType;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.spi.EventBridge;

import java.util.List;

/**
 * Sponge 系事件桥（14.3 Sponge 适配要点）。
 *
 * <p>Sponge 自有 {@code @Listener} 事件总线与 {@code Order}（FIRST→LAST）优先级，命令树基于 Brigadier，
 * 与 Bukkit / Forge 的扫描逻辑不通用，因此这里独立实现一份，不挂到 {@link ModLoaderEventBridge} 上。</p>
 *
 * <p>官方没有 mock 服务端与 GameTest，所以本桥不产出 GAME_TEST_SUITE；注册表事件用于表示插件注册
 * 与注册表冻结，ModHash 用于校验装配产物。</p>
 */
public final class SpongeEventBridge implements EventBridge {

    /** Listener 优先级，按 Sponge {@code Order} 从先到后。 */
    private static final List<String> LISTENER_ORDER =
            List.of("FIRST", "EARLY", "DEFAULT", "LATE", "LAST");

    private static final String PLUGIN_ID = "example-sponge-plugin";
    private static final String SHA = "1111111111111111111111111111111111111111111111111111111111111111";

    private final PlatformFamily family;

    public SpongeEventBridge(PlatformFamily family) {
        this.family = family;
    }

    public PlatformFamily family() {
        return family;
    }

    /** Listener 优先级顺序，供报告核对事件钩子时序。 */
    public List<String> listenerOrder() {
        return LISTENER_ORDER;
    }

    @Override
    public EventStream bridge(PlatformKey key) {
        InMemoryEventStream out = SyntheticStreams.core(SyntheticStreams.PLAYER, key);
        out.emit(new Event.Registry(SyntheticStreams.PLAYER, 9, SyntheticStreams.timestamp(9), key,
                "sponge:plugin", PLUGIN_ID, "listener-registered"));
        out.emit(new Event.Registry(SyntheticStreams.PLAYER, 10, SyntheticStreams.timestamp(10), key,
                "minecraft:block", "example:block", "freeze"));
        out.emit(new Event.Tick(SyntheticStreams.PLAYER, 11, SyntheticStreams.timestamp(11), key, 20.0, 5.6));
        out.emit(new Event.ModHash(SyntheticStreams.PLAYER, 12, SyntheticStreams.timestamp(12), key,
                PLUGIN_ID, SHA));
        return out;
    }

    /** 归一化核心事件类型数，供冒烟与测试核对（要求 ≥10）。 */
    public long coreTypeCount(EventStream stream) {
        return stream.stream().map(Event::type).distinct()
                .filter(type -> type != EventType.REGION_TICK)
                .count();
    }
}
