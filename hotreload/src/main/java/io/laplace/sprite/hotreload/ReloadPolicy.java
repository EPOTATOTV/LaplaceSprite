package io.laplace.sprite.hotreload;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * reload 策略（15.2）：冷启动轨拒绝把 Bukkit /reload 当测试手段。
 *
 * <p>理由是 Paper 官方自 1.21.7 起弃用 Bukkit /reload（只把它重定向为 /minecraft:reload 数据包重载）：
 * 插件 reload 会造成内存泄漏、监听器重复注册、调度器损坏，官方口径是「更新插件请完整重启服务端」。</p>
 *
 * <p>要区分开：数据包的 /reload（minecraft:reload）是原版机制，不在此禁令内。</p>
 */
public final class ReloadPolicy {

    private static final List<String> BUKKIT_RELOAD_VARIANTS = List.of(
            "reload", "reload confirm", "bukkit-reload", "plugin-reload", "bukkit reload", "plugin reload");

    private static final String REJECTION_REASON =
            "Paper 自 1.21.7 起弃用 Bukkit /reload，理由是插件 reload 导致内存泄漏、监听器重复注册、调度器损坏，"
                    + "官方口径为「更新插件请完整重启服务端」；冷启动轨不接受该步骤，"
                    + "请改走 clean-container → cold-start → smoke";

    public boolean isBukkitReload(String step) {
        String normalized = normalize(step);
        if (normalized.isEmpty() || normalized.startsWith("minecraft:")) {
            return false;
        }
        return BUKKIT_RELOAD_VARIANTS.contains(normalized);
    }

    /** 原版数据包重载：允许。 */
    public boolean isVanillaDataPackReload(String step) {
        String normalized = normalize(step);
        return normalized.equals("minecraft:reload") || normalized.equals("datapack-reload");
    }

    /** 只有 Bukkit 插件 reload 会被拒；数据包 reload 返回空。 */
    public Optional<String> rejectionReason(String step) {
        if (step == null || step.isBlank()) {
            return Optional.empty();
        }
        return isBukkitReload(step) ? Optional.of(REJECTION_REASON) : Optional.empty();
    }

    public String reason() {
        return REJECTION_REASON;
    }

    private static String normalize(String step) {
        if (step == null) {
            return "";
        }
        String normalized = step.trim().toLowerCase(Locale.ROOT);
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.trim();
    }
}
