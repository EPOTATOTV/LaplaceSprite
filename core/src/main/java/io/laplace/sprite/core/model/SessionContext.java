package io.laplace.sprite.core.model;

import java.util.Set;

/**
 * 一个模拟会话的判定上下文（4.1 豁免规则所需的服务端侧状态）。
 *
 * <p>由知道真实状态的一方填写：进程内轨由模拟器填默认值，真实环境轨由平台适配器从服务端读。
 * 平台内核不猜测这些状态。</p>
 *
 * @param gameMode             游戏模式
 * @param allowFlight          服务端是否允许该玩家飞行
 * @param ticksSinceJoin       进服后经过的 tick 数；未知用 {@link Long#MAX_VALUE}
 * @param pluginTeleportTicks  其它插件传送发生的 tick
 * @param pluginKnockbackTicks 其它插件击退发生的 tick
 */
public record SessionContext(GameMode gameMode, boolean allowFlight, long ticksSinceJoin,
                             Set<Long> pluginTeleportTicks, Set<Long> pluginKnockbackTicks) {

    public SessionContext {
        gameMode = gameMode == null ? GameMode.SURVIVAL : gameMode;
        pluginTeleportTicks = pluginTeleportTicks == null ? Set.of() : Set.copyOf(pluginTeleportTicks);
        pluginKnockbackTicks = pluginKnockbackTicks == null ? Set.of() : Set.copyOf(pluginKnockbackTicks);
    }

    /** 默认上下文：生存模式、不允许飞行、早已过开局缓冲、无插件干预。 */
    public static SessionContext defaults() {
        return new SessionContext(GameMode.SURVIVAL, false, Long.MAX_VALUE, Set.of(), Set.of());
    }

    public static SessionContext creative() {
        return new SessionContext(GameMode.CREATIVE, true, Long.MAX_VALUE, Set.of(), Set.of());
    }

    public static SessionContext spectator() {
        return new SessionContext(GameMode.SPECTATOR, true, Long.MAX_VALUE, Set.of(), Set.of());
    }
}
