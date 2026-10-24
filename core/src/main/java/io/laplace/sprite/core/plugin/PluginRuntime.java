package io.laplace.sprite.core.plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 进程级插件门面：CLI 启动时装一次，其余代码只问 {@link #isActive(String)}。
 *
 * <p>没安装时第一次访问走 ServiceLoader 懒加载；{@code laplace.plugins.exclude}
 * 里的逗号分隔 id 会被跳过，用来做故障注入和灰度开关。</p>
 */
public final class PluginRuntime {

    public static final String EXCLUDE_PROPERTY = "laplace.plugins.exclude";

    private static final Object LOCK = new Object();

    private static volatile PluginRegistry registry;

    private PluginRuntime() {
    }

    /** 安装并激活一个注册表；重复安装以最后一次为准。 */
    public static void install(PluginRegistry registry) {
        registry.activateAll();
        synchronized (LOCK) {
            PluginRuntime.registry = registry;
        }
    }

    public static PluginRegistry registry() {
        PluginRegistry current = registry;
        if (current != null) {
            return current;
        }
        synchronized (LOCK) {
            if (registry == null) {
                registry = discover();
                registry.activateAll();
            }
            return registry;
        }
    }

    public static boolean isActive(String id) {
        return registry().isActive(id);
    }

    /** 只从 ServiceLoader 收集插件并应用排除项，不激活。 */
    public static PluginRegistry discover() {
        List<BusinessPlugin> discovered = new ArrayList<>();
        for (BusinessPlugin plugin : ServiceLoader.load(BusinessPlugin.class)) {
            discovered.add(plugin);
        }
        return discover(discovered, excludedIds());
    }

    static PluginRegistry discover(Iterable<BusinessPlugin> discovered, Set<String> excludedIds) {
        PluginRegistry registry = new PluginRegistry();
        for (BusinessPlugin plugin : discovered) {
            if (excludedIds.contains(plugin.descriptor().id())) {
                continue;
            }
            registry.register(plugin);
        }
        return registry;
    }

    static Set<String> excludedIds() {
        String raw = System.getProperty(EXCLUDE_PROPERTY);
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 测试钩子：清掉已安装实例，下次访问重新发现。 */
    public static void reset() {
        synchronized (LOCK) {
            registry = null;
        }
    }
}
