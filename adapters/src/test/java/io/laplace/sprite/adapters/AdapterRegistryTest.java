package io.laplace.sprite.adapters;

import io.laplace.sprite.adapters.bedrock.PowerNukkitXAdapter;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.spi.AdapterRegistry;
import io.laplace.sprite.core.spi.LaplaceAdapter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 适配器注册与兼容链回退（14.2 / 14.3 / 14.8）。 */
class AdapterRegistryTest {

    @Test
    void registersAllTwentyFourAdapters() {
        AdapterRegistry registry = AdapterRegistryFactory.defaultRegistry();
        assertEquals(24, registry.size());
        assertEquals(24, AdapterRegistryFactory.allAdapters().size());
        assertTrue(registry.byId("folia").isPresent());
        assertTrue(registry.byId("velocity").isPresent());
        assertTrue(registry.byId("quilt").isPresent());
        assertTrue(registry.byId("spongeneo").isPresent());
        assertTrue(registry.byId("arclight").isPresent());
    }

    @Test
    void resolvesEveryFamilyExactlyWithoutWarnings() {
        AdapterRegistry registry = AdapterRegistryFactory.defaultRegistry();
        for (AbstractLaplaceAdapter adapter : AdapterRegistryFactory.allAdapters()) {
            PlatformKey key = AdapterTestSupport.keyFor(adapter);
            Optional<LaplaceAdapter> resolved = registry.resolve(key);
            assertTrue(resolved.isPresent(), "未解析到适配器: " + key.id());
            assertEquals(adapter.id(), resolved.get().id(), "矩阵键应精确命中: " + key.id());
        }
        assertTrue(registry.warnings().isEmpty(), "精确命中不应产生降级告警: " + registry.warnings());
    }

    @Test
    void fallsBackPurpurToPaper() {
        assertFallback(PlatformKey.of(PlatformFamily.PURPUR, "1.22.0"), "paper");
    }

    @Test
    void fallsBackQuiltToFabric() {
        assertFallback(new PlatformKey(PlatformFamily.QUILT, "1.21.11", "0.20.0"), "fabric");
    }

    @Test
    void fallsBackPowerNukkitXToNukkit() {
        assertFallback(PlatformKey.of(PlatformFamily.POWER_NUKKIT_X, "1.20.4"), "nukkit");
    }

    @Test
    void fallsBackWaterfallToBungeeCord() {
        assertFallback(PlatformKey.of(PlatformFamily.WATERFALL, "1.21.11"), "bungeecord");
    }

    @Test
    void unknownFamilyProducesWarningAndEmpty() {
        AdapterRegistry registry = AdapterRegistryFactory.defaultRegistry();
        Optional<LaplaceAdapter> resolved = registry.resolve(PlatformKey.of(PlatformFamily.MODPACK, "1.21.11"));
        assertTrue(resolved.isEmpty());
        assertTrue(registry.warnings().stream().anyMatch(warning -> warning.contains("无可用适配器")));
    }

    @Test
    void adaptersExposePlatformFacts() {
        for (AbstractLaplaceAdapter adapter : AdapterRegistryFactory.allAdapters()) {
            assertNotNull(adapter.assemblyDescription(), adapter.id());
            assertFalse(adapter.descriptorFile().isBlank(), adapter.id());
            assertFalse(adapter.schedulerModel().isBlank(), adapter.id());
            assertFalse(adapter.testAdaptationNotes().isEmpty(), adapter.id());
            assertNotNull(adapter.adapterStatus(), adapter.id());
        }
    }

    @Test
    void specialStatusesMatchMatrix() {
        AdapterRegistry registry = AdapterRegistryFactory.defaultRegistry();
        assertEquals(AdapterStatus.EOL, statusOf(registry, "waterfall"));
        assertEquals(AdapterStatus.COMPAT_SHELL, statusOf(registry, "quilt"));
        assertEquals(AdapterStatus.CONCURRENT_VARIANT, statusOf(registry, "folia"));
        assertEquals(AdapterStatus.HOST_MOUNTED, statusOf(registry, "geyser"));
        assertEquals(AdapterStatus.IN_PROGRESS, statusOf(registry, "allay"));
        assertEquals(AdapterStatus.IMPLEMENTED_TIER1, statusOf(registry, "paper"));
    }

    @Test
    void powerNukkitXRequiresJdk21() {
        LaplaceAdapter adapter = AdapterRegistryFactory.defaultRegistry().byId("powernukkitx").orElseThrow();
        assertEquals(21, ((PowerNukkitXAdapter) adapter).requiredJdk());
    }

    @Test
    void allAdaptersPassTheThreeSmokePlays() {
        SmokeScenarios smoke = new SmokeScenarios();
        List<SmokeScenarios.SmokeResult> results = AdapterRegistryFactory.allAdapters().stream()
                .flatMap(adapter -> smoke.run(adapter, AdapterTestSupport.keyFor(adapter)).stream())
                .toList();
        assertEquals(24 * 3, results.size());
        List<SmokeScenarios.SmokeResult> failures = results.stream()
                .filter(result -> !result.passed())
                .toList();
        assertTrue(failures.isEmpty(), "冒烟未通过: " + failures);
        assertTrue(SmokeScenarios.REQUIRED_PLAYS.containsAll(
                results.stream().map(SmokeScenarios.SmokeResult::scenario).distinct().toList()));
    }

    private static void assertFallback(PlatformKey key, String expectedAdapterId) {
        AdapterRegistry registry = AdapterRegistryFactory.defaultRegistry();
        Optional<LaplaceAdapter> resolved = registry.resolve(key);
        assertTrue(resolved.isPresent(), "回退失败: " + key.id());
        assertEquals(expectedAdapterId, resolved.get().id());
        assertTrue(registry.warnings().stream().anyMatch(warning -> warning.contains("回退到")),
                "回退必须输出降级告警: " + registry.warnings());
    }

    private static AdapterStatus statusOf(AdapterRegistry registry, String id) {
        LaplaceAdapter adapter = registry.byId(id).orElseThrow();
        return ((AbstractLaplaceAdapter) adapter).adapterStatus();
    }
}
