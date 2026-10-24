package io.laplace.sprite.core.plugin;

import io.laplace.sprite.core.i18n.I18n;
import io.laplace.sprite.core.spi.AdapterRegistry;

import java.util.Map;
import java.util.Objects;

/**
 * 插件拿到的内核句柄：事件总线、适配器注册表、只读配置、多语言门面。
 *
 * @param events   内核事件总线
 * @param adapters 适配器注册表
 * @param settings 插件配置项（只读快照）
 * @param i18n     文案门面；缺省用进程级单例，插件可在 {@code onLoad} 里注册自己的语言包
 */
public record PluginContext(KernelEventBus events, AdapterRegistry adapters, Map<String, String> settings,
                            I18n i18n) {

    public PluginContext {
        Objects.requireNonNull(events, "events");
        Objects.requireNonNull(adapters, "adapters");
        settings = settings == null ? Map.of() : Map.copyOf(settings);
        i18n = i18n == null ? I18n.get() : i18n;
    }

    public PluginContext(KernelEventBus events, AdapterRegistry adapters, Map<String, String> settings) {
        this(events, adapters, settings, I18n.get());
    }

    public PluginContext(KernelEventBus events, AdapterRegistry adapters) {
        this(events, adapters, Map.of());
    }
}
