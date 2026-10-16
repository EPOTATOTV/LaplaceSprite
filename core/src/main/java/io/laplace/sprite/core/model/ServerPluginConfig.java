package io.laplace.sprite.core.model;

import java.util.List;

/**
 * 受测插件配置（11.6 插件配置）。
 *
 * <p>服务端模式默认仅告警：{@code kickEnabled} / {@code banEnabled} 默认 off。</p>
 */
public record ServerPluginConfig(boolean detectionEnabled, boolean kickEnabled, boolean banEnabled,
                                 int alertThreshold, List<String> exemptions, String logLevel) {

    public ServerPluginConfig {
        exemptions = exemptions == null ? List.of() : List.copyOf(exemptions);
    }

    public static ServerPluginConfig safeDefaults() {
        return new ServerPluginConfig(true, false, false, 60,
                List.of("CREATIVE", "SPECTATOR", "ALLOW_FLIGHT", "JOIN_GRACE"), "DEBUG");
    }
}
