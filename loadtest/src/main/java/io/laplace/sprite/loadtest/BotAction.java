package io.laplace.sprite.loadtest;

/**
 * 压测动作类型（15.9）。
 *
 * <p>动作词汇与行为模型保持一致：进站、走动、挖掘、放置、攻击、聊天、待机。
 * 具体后端如何渲染由各自的启动器决定，DSL 本身不绑定实现。</p>
 */
public enum BotAction {

    /** 登录进服。 */
    JOIN,
    /** 走动，推动区块加载。 */
    WALK,
    /** 挖掘方块。 */
    MINE,
    /** 放置方块。 */
    PLACE,
    /** 攻击实体。 */
    ATTACK,
    /** 发送聊天 / 指令包。 */
    CHAT,
    /** 在 26.x 世界里批量生成 / 牵引实体，压实体 tick。 */
    ENTITY_LOAD,
    /** 以固定半径绕圈推进，持续加载新区块。 */
    CHUNK_LOAD,
    /** 原地待机。 */
    IDLE
}
