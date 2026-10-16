package io.laplace.sprite.hotreload;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 资源变更路由表（15.2）：{@code assets/} → F3+T 等价事件；{@code data/} → 原版数据包 /reload；
 * mods / plugins → 强制冷重启。
 *
 * <p>这里的 /reload 指原版数据包重载（minecraft:reload），与冷启动轨禁止的 Bukkit 插件 reload 不是一回事：
 * 前者是原版机制，后者会泄漏内存、重复注册监听器。</p>
 */
public final class ResourceChangeRouter {

    public static final String ACTION_ASSETS_RELOAD = "assets-reload（F3+T 等价）";

    public static final String ACTION_DATAPACK_RELOAD = "vanilla-datapack-reload（原版 /reload）";

    public static final String ACTION_FORCED_RESTART = "forced-cold-restart";

    public static final String ACTION_NONE = "none";

    /**
     * 路由结论。
     *
     * @param path   被改动的路径
     * @param action 应采取的动作
     * @param track  归属轨道
     * @param reason 判定依据
     */
    public record RouteDecision(String path, String action, HotReloadTrack track, String reason) {
    }

    public RouteDecision route(String changedPath) {
        if (changedPath == null || changedPath.isBlank()) {
            throw new IllegalArgumentException("改动路径不能为空");
        }
        List<String> segments = segments(changedPath);
        if (segments.contains("mods") || segments.contains("plugins")) {
            return new RouteDecision(changedPath, ACTION_FORCED_RESTART, HotReloadTrack.COLD_LIFECYCLE,
                    "mods/plugins 目录变更不参与热更新，强制冷重启");
        }
        if (segments.contains("assets")) {
            return new RouteDecision(changedPath, ACTION_ASSETS_RELOAD, HotReloadTrack.DEV_HOT_LOOP,
                    "客户端资源变更触发 F3+T 等价重载");
        }
        if (segments.contains("data")) {
            return new RouteDecision(changedPath, ACTION_DATAPACK_RELOAD, HotReloadTrack.DEV_HOT_LOOP,
                    "数据包变更走原版 /reload（minecraft:reload），不是 Bukkit 插件 reload");
        }
        return new RouteDecision(changedPath, ACTION_NONE, HotReloadTrack.DEV_HOT_LOOP,
                "未命中资源路由表，按普通改动处理");
    }

    private static List<String> segments(String path) {
        return Arrays.stream(path.replace('\\', '/').toLowerCase(Locale.ROOT).split("/"))
                .filter(segment -> !segment.isBlank())
                .toList();
    }
}
