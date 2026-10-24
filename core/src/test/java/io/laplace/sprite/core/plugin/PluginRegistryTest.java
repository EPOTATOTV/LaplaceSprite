package io.laplace.sprite.core.plugin;

import io.laplace.sprite.core.spi.AdapterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 插件注册表：依赖顺序、缺依赖、循环依赖、幂等、能力查找与排除。 */
class PluginRegistryTest {

    @AfterEach
    void clearRuntime() {
        PluginRuntime.reset();
    }

    @Test
    void activatesDependenciesBeforeDependents() {
        PluginRegistry registry = new PluginRegistry();
        List<String> loads = new ArrayList<>();
        registry.register(new TestPlugin("pacc", List.of("base"), List.of("pacc"), loads));
        registry.register(new TestPlugin("base", List.of(), List.of(), loads));

        registry.activateAll();

        assertEquals(List.of("base", "pacc"), loads);
        assertTrue(registry.isActive("pacc"));
    }

    @Test
    void skipsPluginWithMissingDependencyAndRecordsError() {
        PluginRegistry registry = new PluginRegistry();
        TestPlugin pacc = new TestPlugin("pacc", List.of("absent"), List.of("pacc"));
        registry.register(pacc);

        registry.activateAll();

        assertFalse(registry.isActive("pacc"));
        assertEquals(0, pacc.loadCount());
        assertTrue(registry.errors().stream()
                .anyMatch(error -> error.contains("pacc") && error.contains("absent")));
    }

    @Test
    void reportsCycleInsteadOfLoopingForever() {
        PluginRegistry registry = new PluginRegistry();
        registry.register(new TestPlugin("a", List.of("b"), List.of()));
        registry.register(new TestPlugin("b", List.of("a"), List.of()));

        registry.activateAll();

        assertFalse(registry.isActive("a"));
        assertFalse(registry.isActive("b"));
        assertTrue(registry.errors().stream().anyMatch(error -> error.contains("循环依赖")),
                registry.errors().toString());
    }

    @Test
    void registeringAnActivePluginAgainIsIdempotent() {
        PluginRegistry registry = new PluginRegistry();
        TestPlugin first = new TestPlugin("pacc", List.of(), List.of("pacc"));
        assertTrue(registry.register(first));
        registry.activateAll();

        assertFalse(registry.register(new TestPlugin("pacc", List.of(), List.of("pacc"))));
        registry.activateAll();

        assertEquals(1, first.loadCount());
        assertEquals(1, registry.byCapability("pacc").size());
    }

    @Test
    void byCapabilityReturnsActivePluginsOnly() {
        PluginRegistry registry = new PluginRegistry();
        registry.register(new TestPlugin("pacc", List.of(), List.of("pacc")));
        registry.register(new TestPlugin("broken", List.of("absent"), List.of("pacc")));

        registry.activateAll();

        List<BusinessPlugin> pacc = registry.byCapability("pacc");
        assertEquals(1, pacc.size());
        assertEquals("pacc", pacc.get(0).descriptor().id());
    }

    @Test
    void unloadRunsPluginHookAndDeactivatesIt() {
        PluginRegistry registry = new PluginRegistry();
        TestPlugin plugin = new TestPlugin("pacc", List.of(), List.of("pacc"));
        registry.register(plugin);
        registry.activateAll();

        registry.unload("pacc");

        assertFalse(registry.isActive("pacc"));
        assertEquals(1, plugin.unloadCount());
        assertTrue(registry.descriptors().isEmpty());
    }

    @Test
    void pluginContextCarriesSettingsAndAdapterRegistry() {
        AdapterRegistry adapters = new AdapterRegistry();
        PluginRegistry registry = new PluginRegistry(new KernelEventBus(), adapters, Map.of("mode", "nightly"));
        List<PluginContext> contexts = new ArrayList<>();
        registry.register(new BusinessPlugin() {
            @Override
            public PluginDescriptor descriptor() {
                return new PluginDescriptor("ctx", "上下文插件", "1.0.0", List.of(), List.of());
            }

            @Override
            public void onLoad(PluginContext context) {
                contexts.add(context);
            }
        });

        registry.activateAll();

        assertEquals("nightly", contexts.get(0).settings().get("mode"));
        assertEquals(adapters, contexts.get(0).adapters());
        assertTrue(contexts.get(0).events() instanceof KernelEventBus);
    }

    @Test
    void excludingOnePluginKeepsOthersActive() {
        PluginRegistry registry = PluginRuntime.discover(
                List.of(new TestPlugin("pacc", List.of(), List.of("pacc")),
                        new TestPlugin("core-test", List.of(), List.of("core-test"))),
                Set.of("pacc"));

        registry.activateAll();

        assertFalse(registry.isActive("pacc"));
        assertTrue(registry.isActive("core-test"));
    }

    @Test
    void runtimeDiscoversPluginsFromServiceLoader() {
        PluginRuntime.reset();

        assertTrue(PluginRuntime.isActive("core-test"));
    }

    private static final class TestPlugin implements BusinessPlugin {

        private final PluginDescriptor descriptor;
        private final List<String> loadOrder;
        private int loadCount;
        private int unloadCount;

        private TestPlugin(String id, List<String> requires, List<String> capabilities) {
            this(id, requires, capabilities, new ArrayList<>());
        }

        private TestPlugin(String id, List<String> requires, List<String> capabilities, List<String> loadOrder) {
            this.descriptor = new PluginDescriptor(id, id, "1.0.0", requires, capabilities);
            this.loadOrder = loadOrder;
        }

        @Override
        public PluginDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public void onLoad(PluginContext context) {
            loadCount++;
            loadOrder.add(descriptor.id());
        }

        @Override
        public void onUnload() {
            unloadCount++;
        }

        private int loadCount() {
            return loadCount;
        }

        private int unloadCount() {
            return unloadCount;
        }
    }
}
