package io.laplace.sprite.core.plugin;

import io.laplace.sprite.core.spi.AdapterRegistry;

import java.util.Map;
import java.util.Objects;

/**
 * 插件拿到的内核句柄：事件总线、适配器注册表、只读配置。
 *
 * @param events   内核事件总线
 * @param adapters 适配器注册表
 * @param settings 插件配置项（只读快照）
 */
public record PluginContext(KernelEventBus events, AdapterRegistry adapters, Map<String, String> settings) {

    public PluginContext {
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(adapters, "adapters");
        settings = settings == null ? Map.of() : Map.copyOf(settings);
    }

    public PluginContext(KernelEventBus events, AdapterRegistry adapters) {
        this(events, adapters, Map.of());
    }
}
