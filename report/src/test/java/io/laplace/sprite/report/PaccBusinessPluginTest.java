package io.laplace.sprite.report;

import io.laplace.sprite.core.plugin.KernelEvent;
import io.laplace.sprite.core.plugin.KernelHook;
import io.laplace.sprite.core.plugin.PluginDescriptor;
import io.laplace.sprite.core.plugin.PluginRegistry;
import io.laplace.sprite.core.plugin.PluginRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PACC 业务插件：ServiceLoader 发现、生命周期订阅、排除开关。 */
class PaccBusinessPluginTest {

    @AfterEach
    void clearRuntime() {
        PluginRuntime.reset();
    }

    @Test
    void descriptorDeclaresPaccCapability() {
        PluginDescriptor descriptor = new PaccBusinessPlugin().descriptor();

        assertEquals("pacc", descriptor.id());
        assertEquals("1.5.0", descriptor.version());
        assertTrue(descriptor.capabilities().contains("pacc"));
        assertTrue(descriptor.requires().isEmpty());
    }

    @Test
    void countsLifecycleEventsAndStopsAfterUnload() {
        PluginRegistry registry = new PluginRegistry();
        PaccBusinessPlugin plugin = new PaccBusinessPlugin();
        registry.register(plugin);
        registry.activateAll();

        registry.events().publish(event(KernelHook.TEST_LIFECYCLE));
        registry.events().publish(event(KernelHook.ENTITY_TICK));
        assertEquals(1, plugin.observedLifecycleEvents());

        registry.unload(PaccBusinessPlugin.PLUGIN_ID);
        registry.events().publish(event(KernelHook.TEST_LIFECYCLE));
        assertEquals(1, plugin.observedLifecycleEvents());
    }

    @Test
    void pluginIsDiscoveredThroughServiceLoader() {
        PluginRuntime.reset();

        assertTrue(PluginRuntime.isActive(PaccBusinessPlugin.PLUGIN_ID));
        assertEquals(1, PluginRuntime.registry().byCapability("pacc").size());
    }

    @Test
    void exclusionPropertyDisablesPacc() {
        String previous = System.getProperty(PluginRuntime.EXCLUDE_PROPERTY);
        try {
            System.clearProperty(PluginRuntime.EXCLUDE_PROPERTY);
            PluginRuntime.reset();
            assertTrue(PluginRuntime.isActive(PaccBusinessPlugin.PLUGIN_ID));

            System.setProperty(PluginRuntime.EXCLUDE_PROPERTY, PaccBusinessPlugin.PLUGIN_ID);
            PluginRuntime.reset();

            assertFalse(PluginRuntime.isActive(PaccBusinessPlugin.PLUGIN_ID));
        } finally {
            if (previous == null) {
                System.clearProperty(PluginRuntime.EXCLUDE_PROPERTY);
            } else {
                System.setProperty(PluginRuntime.EXCLUDE_PROPERTY, previous);
            }
            PluginRuntime.reset();
        }
    }

    private static KernelEvent event(KernelHook hook) {
        return new KernelEvent(hook, "pacc", Instant.now(), Map.of());
    }
}
