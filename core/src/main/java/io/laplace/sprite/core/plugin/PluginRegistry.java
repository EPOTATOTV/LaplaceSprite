package io.laplace.sprite.core.plugin;

import io.laplace.sprite.core.spi.AdapterRegistry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 插件注册与激活：按 requires 拓扑排序，缺依赖跳过、循环依赖报错。
 *
 * <p>注册和激活都在注册表锁内完成，插件 {@code onLoad} 里再查 {@link #isActive(String)}
 * 是同一线程重入，不会死锁；已激活的插件重复注册是无操作。</p>
 */
public final class PluginRegistry {

    private final KernelEventBus events;
    private final AdapterRegistry adapters;
    private final Map<String, String> settings;
    private final Map<String, BusinessPlugin> plugins = new LinkedHashMap<>();
    private final Set<String> active = new LinkedHashSet<>();
    private final List<String> errors = new CopyOnWriteArrayList<>();

    public PluginRegistry(KernelEventBus events, AdapterRegistry adapters, Map<String, String> settings) {
        this.events = Objects.requireNonNull(events, "events");
        this.adapters = Objects.requireNonNull(adapters, "adapters");
        this.settings = settings == null ? Map.of() : Map.copyOf(settings);
    }

    public PluginRegistry(KernelEventBus events, AdapterRegistry adapters) {
        this(events, adapters, Map.of());
    }

    public PluginRegistry() {
        this(new KernelEventBus(), new AdapterRegistry());
    }

    /**
     * 登记插件，不激活。
     *
     * @return 首次登记返回 true；同 id 已登记（无论是否激活）返回 false
     */
    public synchronized boolean register(BusinessPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        String id = plugin.descriptor().id();
        if (plugins.containsKey(id)) {
            return false;
        }
        plugins.put(id, plugin);
        return true;
    }

    /** 按依赖顺序激活全部已登记插件；可重复调用。 */
    public synchronized void activateAll() {
        Set<String> cyclic = new LinkedHashSet<>();
        List<String> order = dependencyOrder(cyclic);
        for (String id : order) {
            if (active.contains(id) || cyclic.contains(id)) {
                continue;
            }
            BusinessPlugin plugin = plugins.get(id);
            List<String> missing = plugin.descriptor().requires().stream()
                    .filter(require -> !active.contains(require))
                    .toList();
            if (!missing.isEmpty()) {
                errors.add("插件 %s 缺少依赖 %s，已跳过".formatted(id, String.join("、", missing)));
                continue;
            }
            try {
                plugin.onLoad(new PluginContext(events, adapters, settings));
                active.add(id);
            } catch (RuntimeException ex) {
                errors.add("插件 %s 加载失败: %s".formatted(id, ex));
            }
        }
    }

    public synchronized boolean isActive(String id) {
        return active.contains(id);
    }

    /** 按能力查已激活插件。 */
    public synchronized List<BusinessPlugin> byCapability(String capability) {
        return active.stream()
                .map(plugins::get)
                .filter(plugin -> plugin.descriptor().capabilities().contains(capability))
                .toList();
    }

    /** 卸载并注销插件；未激活时静默返回。 */
    public synchronized void unload(String id) {
        BusinessPlugin plugin = plugins.get(id);
        if (plugin == null) {
            return;
        }
        if (active.remove(id)) {
            try {
                plugin.onUnload();
            } catch (RuntimeException ex) {
                errors.add("插件 %s 卸载失败: %s".formatted(id, ex));
            }
        }
        plugins.remove(id);
    }

    public synchronized List<PluginDescriptor> descriptors() {
        return plugins.values().stream().map(BusinessPlugin::descriptor).toList();
    }

    public List<String> errors() {
        return List.copyOf(errors);
    }

    public KernelEventBus events() {
        return events;
    }

    public AdapterRegistry adapters() {
        return adapters;
    }

    public Map<String, String> settings() {
        return settings;
    }

    /** 深度优先得到激活顺序（依赖先于依赖方），同时收集环上的插件。 */
    private List<String> dependencyOrder(Set<String> cyclic) {
        Map<String, Integer> state = new HashMap<>();
        List<String> order = new ArrayList<>();
        Deque<String> stack = new ArrayDeque<>();
        for (String id : plugins.keySet()) {
            visit(id, state, order, stack, cyclic);
        }
        return order;
    }

    private void visit(String id, Map<String, Integer> state, List<String> order, Deque<String> stack,
                       Set<String> cyclic) {
        Integer status = state.get(id);
        if (status != null) {
            if (status == 1) {
                recordCycle(id, stack, cyclic);
            }
            return;
        }
        BusinessPlugin plugin = plugins.get(id);
        if (plugin == null) {
            return;
        }
        state.put(id, 1);
        stack.addLast(id);
        for (String require : plugin.descriptor().requires()) {
            visit(require, state, order, stack, cyclic);
        }
        stack.removeLast();
        state.put(id, 2);
        order.add(id);
    }

    private void recordCycle(String id, Deque<String> stack, Set<String> cyclic) {
        List<String> cycle = new ArrayList<>();
        boolean inside = false;
        for (String each : stack) {
            if (each.equals(id)) {
                inside = true;
            }
            if (inside) {
                cycle.add(each);
            }
        }
        cycle.add(id);
        cyclic.addAll(cycle);
        errors.add("循环依赖: " + String.join(" -> ", cycle));
    }
}
