package io.laplace.sprite.core.plugin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 测试套件提供者：自带套件不受插件影响，插件套件随加载状态开关。 */
class TestSuiteProviderTest {

    @AfterEach
    void clearRuntime() {
        PluginRuntime.reset();
    }

    @Test
    void builtInLegitSuiteIsAlwaysSupported() {
        PluginRegistry registry = new PluginRegistry();

        assertTrue(registry.supportsSuite("legit"));
        assertTrue(PluginRuntime.supportsSuite("legit"));
        assertFalse(registry.supportsSuite("boundary"), "boundary 依赖 PACC 样本，不能算内核自带套件");
    }

    @Test
    void pluginSuitesBecomeSupportedAfterActivation() {
        PluginRegistry registry = new PluginRegistry();
        registry.register(new SuitePlugin("pacc", List.of("cheat", "mixed")));

        assertFalse(registry.supportsSuite("cheat"));

        registry.activateAll();

        assertTrue(registry.supportsSuite("cheat"));
        assertTrue(registry.supportsSuite("mixed"));
        assertTrue(registry.supportsSuite("legit"));
    }

    @Test
    void excludedPluginLeavesSuitesUnsupported() {
        PluginRegistry registry = PluginRuntime.discover(
                List.of(new SuitePlugin("pacc", List.of("cheat", "mixed"))), Set.of("pacc"));
        registry.activateAll();

        assertFalse(registry.supportsSuite("cheat"));
        assertFalse(registry.supportsSuite("mixed"));
    }

    @Test
    void unloadingPluginDropsSuites() {
        PluginRegistry registry = new PluginRegistry();
        registry.register(new SuitePlugin("pacc", List.of("cheat", "mixed")));
        registry.activateAll();

        registry.unload("pacc");

        assertFalse(registry.supportsSuite("cheat"));
        assertFalse(registry.supportsSuite("mixed"));
    }

    @Test
    void suiteIdsOnlyComeFromProviders() {
        PluginRegistry registry = new PluginRegistry();
        registry.register(new SuitePlugin("pacc", List.of("cheat", "mixed")));
        registry.register(new TestCorePlugin());
        registry.activateAll();

        assertEquals(List.of("cheat", "mixed"), registry.suiteIds("pacc"));
        assertTrue(registry.suiteIds("core-test").isEmpty());
    }

    private static final class SuitePlugin implements BusinessPlugin, TestSuiteProvider {

        private final PluginDescriptor descriptor;
        private final List<String> suiteIds;

        private SuitePlugin(String id, List<String> suiteIds) {
            this.descriptor = new PluginDescriptor(id, id, "1.0.0", List.of(), List.of(id, "test-suites"));
            this.suiteIds = suiteIds;
        }

        @Override
        public PluginDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public List<String> suiteIds() {
            return suiteIds;
        }

        @Override
        public void onLoad(PluginContext context) {
        }
    }
}
