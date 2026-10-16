package io.laplace.sprite.core.event;

import io.laplace.sprite.core.model.PlatformKey;

import java.time.Instant;
import java.util.Map;

/**
 * 归一化内部事件流的基本单元（第五章事件流契约 / 14.6 事件归一化模型）。
 *
 * <p>所有事件都携带玩家标识、tick、时间戳与平台标签；平台特有上下文（Folia 区域 tick、
 * Velocity Continuation id、BungeeCord intent）统一放进 {@link #context()}，对断言引擎透明。</p>
 */
public sealed interface Event {

    String playerId();

    long tick();

    Instant timestamp();

    PlatformKey platform();

    /** 平台特有上下文，默认空。 */
    default Map<String, Object> context() {
        return Map.of();
    }

    default EventType type() {
        return switch (this) {
            case PlayerJoin ignored -> EventType.PLAYER_JOIN;
            case PlayerLeave ignored -> EventType.PLAYER_LEAVE;
            case ProxyPlayerJoin ignored -> EventType.PROXY_PLAYER_JOIN;
            case ProxyPlayerLeave ignored -> EventType.PROXY_PLAYER_LEAVE;
            case ProxyChat ignored -> EventType.PROXY_CHAT;
            case Disconnect ignored -> EventType.DISCONNECT;
            case Move ignored -> EventType.MOVE;
            case Combat ignored -> EventType.COMBAT;
            case Interact ignored -> EventType.INTERACT;
            case Chat ignored -> EventType.CHAT;
            case Inventory ignored -> EventType.INVENTORY;
            case Packet ignored -> EventType.PACKET;
            case ChunkLoad ignored -> EventType.CHUNK_LOAD;
            case BlockChange ignored -> EventType.BLOCK_CHANGE;
            case DataConsistency ignored -> EventType.DATA_CONSISTENCY;
            case Brand ignored -> EventType.BRAND;
            case ModHash ignored -> EventType.MOD_HASH;
            case Addon ignored -> EventType.ADDON;
            case RegionTick ignored -> EventType.REGION_TICK;
            case Tick ignored -> EventType.TICK;
            case Registry ignored -> EventType.REGISTRY;
            case GameTestSuite ignored -> EventType.GAME_TEST_SUITE;
        };
    }

    record PlayerJoin(String playerId, long tick, Instant timestamp, PlatformKey platform,
                      String world, double x, double y, double z) implements Event {
    }

    record PlayerLeave(String playerId, long tick, Instant timestamp, PlatformKey platform,
                       String reason) implements Event {
    }

    record ProxyPlayerJoin(String playerId, long tick, Instant timestamp, PlatformKey platform,
                           String targetServer) implements Event {
    }

    record ProxyPlayerLeave(String playerId, long tick, Instant timestamp, PlatformKey platform,
                            String targetServer, String reason) implements Event {
    }

    record ProxyChat(String playerId, long tick, Instant timestamp, PlatformKey platform,
                     String message) implements Event {
    }

    record Disconnect(String playerId, long tick, Instant timestamp, PlatformKey platform,
                      String reason) implements Event {
    }

    record Move(String playerId, long tick, Instant timestamp, PlatformKey platform,
                double x, double y, double z, float yaw, float pitch,
                boolean onGround, boolean sprinting, boolean sneaking) implements Event {
    }

    record Combat(String playerId, long tick, Instant timestamp, PlatformKey platform,
                  String targetId, double reach, int clicksPerSecond, String swingType) implements Event {
    }

    record Interact(String playerId, long tick, Instant timestamp, PlatformKey platform,
                    String blockType, int blockX, int blockY, int blockZ, String action) implements Event {
    }

    record Chat(String playerId, long tick, Instant timestamp, PlatformKey platform,
                String message) implements Event {
    }

    record Inventory(String playerId, long tick, Instant timestamp, PlatformKey platform,
                     String slot, String itemId, int amount) implements Event {
    }

    record Packet(String playerId, long tick, Instant timestamp, PlatformKey platform,
                  String direction, String packetType, int sizeBytes) implements Event {
    }

    record ChunkLoad(String playerId, long tick, Instant timestamp, PlatformKey platform,
                     int chunkX, int chunkZ) implements Event {
    }

    record BlockChange(String playerId, long tick, Instant timestamp, PlatformKey platform,
                       int blockX, int blockY, int blockZ, String from, String to) implements Event {
    }

    record DataConsistency(String playerId, long tick, Instant timestamp, PlatformKey platform,
                           String key, String clientValue, String serverValue) implements Event {
    }

    record Brand(String playerId, long tick, Instant timestamp, PlatformKey platform,
                 String brand) implements Event {
    }

    record ModHash(String playerId, long tick, Instant timestamp, PlatformKey platform,
                   String modId, String sha256) implements Event {
    }

    record Addon(String playerId, long tick, Instant timestamp, PlatformKey platform,
                 String addonId, String version) implements Event {
    }

    record RegionTick(String playerId, long tick, Instant timestamp, PlatformKey platform,
                      String regionId, Map<String, Object> regionContext) implements Event {
        @Override
        public Map<String, Object> context() {
            return regionContext == null ? Map.of() : Map.copyOf(regionContext);
        }
    }

    record Tick(String playerId, long tick, Instant timestamp, PlatformKey platform,
                double tps, double mspt) implements Event {
    }

    record Registry(String playerId, long tick, Instant timestamp, PlatformKey platform,
                    String registry, String entry, String phase) implements Event {
    }

    record GameTestSuite(String playerId, long tick, Instant timestamp, PlatformKey platform,
                         String suite, String testName, boolean passed, String failure) implements Event {
    }
}
