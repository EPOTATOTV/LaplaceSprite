package io.laplace.sprite.loadtest;

/**
 * 假玩家压测后端（15.9）。
 *
 * <p>两种后端互补：{@link #MINEFLAYER} 在容器外拉起真实登录会话，压的是连接与区块加载；
 * {@link #CARPET} 在服务端内生成假人，行为贴近真实玩家但不占网络连接。</p>
 */
public enum BotBackend {

    /** 网络层：Node 进程里的 Mineflayer 会话。 */
    MINEFLAYER,

    /** 服务端内：Fabric + Carpet 的 EntityPlayerMPFake。 */
    CARPET
}
