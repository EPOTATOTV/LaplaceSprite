package io.laplace.sprite.adapters.event;

import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.PlatformKey;

import java.time.Instant;
import java.util.Map;

/**
 * 合成归一化事件流（14.6 / 14.8 第 3 步）。
 *
 * <p>冒烟剧本要求「核心事件归一 ≥10 类」，各家族桥都从这里起步：先铺 12 类公共事件，
 * 再按家族补平台特有事件。时间戳固定在同一个起点，保证同输入同输出。</p>
 */
public final class SyntheticStreams {

    public static final String PLAYER = "SimID-sim";

    /** 一 tick 50ms（20TPS），与其它模块口径一致。 */
    private static final long TICK_MILLIS = 50L;
    private static final Instant ORIGIN = Instant.parse("2026-01-01T00:00:00Z");

    private SyntheticStreams() {
    }

    public static Instant timestamp(long tick) {
        return ORIGIN.plusMillis(tick * TICK_MILLIS);
    }

    /** 12 类公共事件：join/leave/move/packet/chunk/block/interact/inventory/chat/combat/consistency/disconnect。 */
    public static InMemoryEventStream core(String playerId, PlatformKey platform) {
        InMemoryEventStream out = new InMemoryEventStream();
        out.emit(new Event.PlayerJoin(playerId, 0, timestamp(0), platform, "world", 0.5, 64.0, 0.5));
        out.emit(new Event.Move(playerId, 1, timestamp(1), platform, 0.7, 64.0, 0.5, 0f, 0f, true, false, false));
        out.emit(new Event.Packet(playerId, 1, timestamp(1), platform, "SERVERBOUND", "MOVE", 32));
        out.emit(new Event.ChunkLoad(playerId, 2, timestamp(2), platform, 0, 0));
        out.emit(new Event.BlockChange(playerId, 3, timestamp(3), platform, 1, 63, 0,
                "minecraft:air", "minecraft:stone"));
        out.emit(new Event.Interact(playerId, 3, timestamp(3), platform, "minecraft:stone", 1, 63, 0, "place"));
        out.emit(new Event.Inventory(playerId, 4, timestamp(4), platform, "slot-0", "minecraft:stone", 1));
        out.emit(new Event.Chat(playerId, 5, timestamp(5), platform, "hello"));
        out.emit(new Event.Combat(playerId, 6, timestamp(6), platform, "target-1", 2.6, 6, "swing"));
        out.emit(new Event.DataConsistency(playerId, 7, timestamp(7), platform, "position",
                "0.7,64.0,0.5", "0.7,64.0,0.5"));
        out.emit(new Event.PlayerLeave(playerId, 8, timestamp(8), platform, "quit"));
        out.emit(new Event.Disconnect(playerId, 8, timestamp(8), platform, "client disconnect"));
        return out;
    }

    /** 插件端：公共事件 + 客户端 brand 与 tick 采样。 */
    public static InMemoryEventStream plugin(String playerId, PlatformKey platform) {
        InMemoryEventStream out = core(playerId, platform);
        out.emit(new Event.Brand(playerId, 9, timestamp(9), platform, "vanilla"));
        out.emit(new Event.Tick(playerId, 10, timestamp(10), platform, 20.0, 6.4));
        return out;
    }

    /** 代理端：公共事件 + intent 完成后的代理事件（join → chat → leave）。 */
    public static InMemoryEventStream proxy(String playerId, PlatformKey platform) {
        InMemoryEventStream out = core(playerId, platform);
        out.emit(new Event.ProxyPlayerJoin(playerId, 9, timestamp(9), platform, "lobby"));
        out.emit(new Event.ProxyChat(playerId, 10, timestamp(10), platform, "hello proxy"));
        out.emit(new Event.ProxyPlayerLeave(playerId, 11, timestamp(11), platform, "lobby", "switch"));
        return out;
    }

    /** Folia：公共事件 + 区域 tick，区域 id 同时进字段与 context。 */
    public static InMemoryEventStream folia(String playerId, PlatformKey platform) {
        InMemoryEventStream out = core(playerId, platform);
        out.emit(new Event.RegionTick(playerId, 9, timestamp(9), platform, "region-0-0",
                Map.of("regionId", "region-0-0", "owners", 1)));
        out.emit(new Event.RegionTick(playerId, 10, timestamp(10), platform, "region-0-1",
                Map.of("regionId", "region-0-1", "owners", 1)));
        return out;
    }

    /** 基岩：公共事件 + 附加组件与 brand。 */
    public static InMemoryEventStream bedrock(String playerId, PlatformKey platform) {
        InMemoryEventStream out = core(playerId, platform);
        out.emit(new Event.Addon(playerId, 9, timestamp(9), platform, "geyser-addon", "2.4.2"));
        out.emit(new Event.Brand(playerId, 10, timestamp(10), platform, "bedrock-1.21"));
        return out;
    }

    /** 模组加载器：公共事件 + 注册表快照、GameTest 套件、tick 指标与模组哈希。 */
    public static InMemoryEventStream modloader(String playerId, PlatformKey platform) {
        InMemoryEventStream out = core(playerId, platform);
        out.emit(new Event.Registry(playerId, 9, timestamp(9), platform, "minecraft:block", "example:block",
                "freeze"));
        out.emit(new Event.GameTestSuite(playerId, 10, timestamp(10), platform, "smoke", "loads", true, null));
        out.emit(new Event.Tick(playerId, 10, timestamp(10), platform, 20.0, 5.1));
        out.emit(new Event.ModHash(playerId, 11, timestamp(11), platform, "examplemod",
                "0000000000000000000000000000000000000000000000000000000000000000"));
        return out;
    }
}
