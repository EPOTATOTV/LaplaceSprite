package io.laplace.sprite.adapters;

import io.laplace.sprite.adapters.event.BedrockEventBridge;
import io.laplace.sprite.adapters.event.BukkitEventBridge;
import io.laplace.sprite.adapters.event.BungeeCordEventBridge;
import io.laplace.sprite.adapters.event.FoliaEventBridge;
import io.laplace.sprite.adapters.event.ModLoaderEventBridge;
import io.laplace.sprite.adapters.event.VelocityEventBridge;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.event.EventType;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 事件归一化（14.6 / 14.8 验收：每套桥 ≥10 类核心事件）。 */
class EventBridgeTest {

    private static final PlatformKey PAPER = PlatformKey.of(PlatformFamily.PAPER, "1.21.11");
    private static final PlatformKey FOLIA = PlatformKey.of(PlatformFamily.FOLIA, "1.21.11");
    private static final PlatformKey BUNGEE = PlatformKey.of(PlatformFamily.BUNGEECORD, "1.21.11");
    private static final PlatformKey NUKKIT = PlatformKey.of(PlatformFamily.NUKKIT, "1.20.4");
    private static final PlatformKey FABRIC = new PlatformKey(PlatformFamily.FABRIC, "1.21.11", "0.19.5");

    private static final List<EventType> CORE_TYPES = List.of(
            EventType.PLAYER_JOIN, EventType.PLAYER_LEAVE, EventType.MOVE, EventType.INTERACT,
            EventType.CHAT, EventType.INVENTORY, EventType.PACKET, EventType.CHUNK_LOAD,
            EventType.BLOCK_CHANGE, EventType.DATA_CONSISTENCY, EventType.PROXY_PLAYER_JOIN,
            EventType.PROXY_PLAYER_LEAVE, EventType.PROXY_CHAT, EventType.DISCONNECT, EventType.COMBAT,
            EventType.BRAND, EventType.MOD_HASH, EventType.ADDON, EventType.REGISTRY,
            EventType.GAME_TEST_SUITE);

    @Test
    void bukkitBridgeNormalisesCoreEvents() {
        assertCore(new BukkitEventBridge(PlatformFamily.SPIGOT).bridge(PAPER));
    }

    @Test
    void foliaBridgeNormalisesCoreEventsAndRegionTick() {
        FoliaEventBridge bridge = new FoliaEventBridge();
        EventStream stream = bridge.bridge(FOLIA);
        assertCore(stream);
        assertEquals(2, bridge.regionCount(stream));
        assertTrue(stream.ofType(EventType.REGION_TICK).stream()
                .allMatch(event -> event.context().containsKey("regionId")));
        assertTrue(stream.ofType(EventType.REGION_TICK).stream()
                .allMatch(event -> String.valueOf(event.context().get("regionId")).startsWith("region-")));
    }

    @Test
    void bungeeBridgeNormalisesProxyEvents() {
        EventStream stream = new BungeeCordEventBridge(PlatformFamily.BUNGEECORD).bridge(BUNGEE);
        assertCore(stream);
        assertEquals(1, stream.ofType(EventType.PROXY_PLAYER_JOIN).size());
        assertEquals(1, stream.ofType(EventType.PROXY_CHAT).size());
        assertEquals(1, stream.ofType(EventType.PROXY_PLAYER_LEAVE).size());
    }

    @Test
    void velocityBridgeNormalisesProxyEvents() {
        EventStream stream = new VelocityEventBridge().bridge(PAPER);
        assertCore(stream);
        assertEquals(1, stream.ofType(EventType.PROXY_CHAT).size());
    }

    @Test
    void bedrockBridgeNormalisesAddonAndBrand() {
        EventStream stream = new BedrockEventBridge(PlatformFamily.NUKKIT).bridge(NUKKIT);
        assertCore(stream);
        assertEquals(1, stream.ofType(EventType.ADDON).size());
        assertEquals(1, stream.ofType(EventType.BRAND).size());
    }

    @Test
    void modLoaderBridgeNormalisesRegistryAndGameTest() {
        EventStream stream = new ModLoaderEventBridge(PlatformFamily.FABRIC).bridge(FABRIC);
        assertCore(stream);
        assertTrue(!stream.ofType(EventType.REGISTRY).isEmpty());
        assertTrue(!stream.ofType(EventType.GAME_TEST_SUITE).isEmpty());
        assertTrue(!stream.ofType(EventType.MOD_HASH).isEmpty());
    }

    @Test
    void bukkitAsyncEventsLandAfterForwarding() {
        BukkitEventBridge bridge = new BukkitEventBridge(PlatformFamily.BUKKIT);
        int before = bridge.bridge(PAPER).ofType(EventType.CHAT).size();
        bridge.forwardAsync(new Event.Chat(AdapterTestSupport.PLAYER, 99, AdapterTestSupport.ORIGIN, PAPER,
                "async"));
        assertEquals(1, bridge.asyncForwardedCount());
        assertEquals(before + 1, bridge.bridge(PAPER).ofType(EventType.CHAT).size());
    }

    @Test
    void bungeeIntentsSuspendUntilCompleted() {
        BungeeCordEventBridge bridge = new BungeeCordEventBridge(PlatformFamily.BUNGEECORD);
        String intent = bridge.registerIntent("join", AdapterTestSupport.PLAYER, "lobby");
        assertEquals(1, bridge.pendingCount(), "意图未完成前应挂起");
        Optional<Event> event = bridge.completeIntent(intent, BUNGEE);
        assertTrue(event.isPresent());
        assertTrue(event.get() instanceof Event.ProxyPlayerJoin);
        assertEquals(0, bridge.pendingCount());
    }

    @Test
    void velocityEventsLandOnlyAfterResume() {
        VelocityEventBridge bridge = new VelocityEventBridge();
        int landedBefore = bridge.bridge(PAPER).size();
        bridge.asyncTask("login", AdapterTestSupport.PLAYER);
        assertEquals(1, bridge.pendingEvents(), "Continuation 恢复前事件不落流");
        assertEquals(landedBefore, bridge.bridge(PAPER).size());
        List<Event> resumed = bridge.resume(PAPER);
        assertEquals(1, resumed.size());
        assertEquals(0, bridge.pendingEvents());
        assertTrue(resumed.get(0) instanceof Event.ProxyPlayerJoin);
    }

    @Test
    void bridgesReportTheirFamily() {
        assertEquals(PlatformFamily.SPIGOT, new BukkitEventBridge(PlatformFamily.SPIGOT).family());
        assertEquals(PlatformFamily.BUNGEECORD, new BungeeCordEventBridge(PlatformFamily.BUNGEECORD).family());
        assertEquals(PlatformFamily.NUKKIT, new BedrockEventBridge(PlatformFamily.NUKKIT).family());
        assertEquals(PlatformFamily.FABRIC, new ModLoaderEventBridge(PlatformFamily.FABRIC).family());
        assertEquals(PlatformFamily.FOLIA, new FoliaEventBridge().family());
    }

    private static void assertCore(EventStream stream) {
        long covered = stream.stream().map(Event::type).distinct().filter(CORE_TYPES::contains).count();
        assertTrue(covered >= 10, "核心事件类型仅 %d 类".formatted(covered));
    }
}
