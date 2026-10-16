package io.laplace.sprite.core.event;

/**
 * 归一化内部事件类型（14.6）。所有平台事件经 EventBridge 收敛到此枚举。
 */
public enum EventType {
    PLAYER_JOIN,
    PLAYER_LEAVE,
    PROXY_PLAYER_JOIN,
    PROXY_PLAYER_LEAVE,
    PROXY_CHAT,
    DISCONNECT,
    MOVE,
    COMBAT,
    INTERACT,
    CHAT,
    INVENTORY,
    PACKET,
    CHUNK_LOAD,
    BLOCK_CHANGE,
    DATA_CONSISTENCY,
    BRAND,
    MOD_HASH,
    ADDON,
    REGION_TICK,
    TICK,
    REGISTRY,
    GAME_TEST_SUITE
}
