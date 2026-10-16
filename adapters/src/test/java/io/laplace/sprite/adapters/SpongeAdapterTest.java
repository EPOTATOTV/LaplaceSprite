package io.laplace.sprite.adapters;

import io.laplace.sprite.adapters.event.SpongeEventBridge;
import io.laplace.sprite.adapters.sponge.SpongeForgeAdapter;
import io.laplace.sprite.adapters.sponge.SpongeJarClassifier;
import io.laplace.sprite.adapters.sponge.SpongeNeoAdapter;
import io.laplace.sprite.adapters.sponge.SpongeVanillaAdapter;
import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.event.EventType;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.spi.AdapterRegistry;
import io.laplace.sprite.core.spi.LaplaceAdapter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sponge 系接入（14.3 / 14.9）：装配事实、描述符识别顺序、事件桥与兼容链。 */
class SpongeAdapterTest {

    private static final List<AbstractLaplaceAdapter> SPONGE = List.of(
            new SpongeVanillaAdapter(), new SpongeNeoAdapter(), new SpongeForgeAdapter());

    private static final PlatformKey SPONGE_NEO_KEY = PlatformKey.of(PlatformFamily.SPONGE_NEO, "1.21.10");

    @Test
    void spongeAdaptersDeclareLayerFacts() {
        for (AbstractLaplaceAdapter adapter : SPONGE) {
            assertEquals(AdapterStatus.IMPLEMENTED_TIER1, adapter.adapterStatus(), adapter.id());
            assertTrue(adapter.assemblyDescription().startsWith("mods/"), adapter.assemblyDescription());
            assertEquals("META-INF/sponge_plugins.json", adapter.descriptorFile(), adapter.id());
            assertTrue(adapter.schedulerModel().contains("@Listener"), adapter.schedulerModel());
            assertTrue(adapter.schedulerModel().contains("Brigadier"), adapter.schedulerModel());
            assertTrue(adapter.testAdaptationNotes().stream()
                            .anyMatch(note -> note.contains("无 mock") || note.contains("GameTest")),
                    adapter.id() + " 缺少无 mock / 无 GameTest 要点");
        }
        assertTrue(SPONGE.get(1).testAdaptationNotes().stream()
                .anyMatch(note -> note.contains("两套事件总线")));
        assertTrue(SPONGE.get(2).testAdaptationNotes().stream()
                .anyMatch(note -> note.contains("先查 sponge_plugins.json")));
    }

    @Test
    void spongeEventBridgeNormalisesCoreEvents() {
        SpongeEventBridge bridge = new SpongeEventBridge(PlatformFamily.SPONGE_NEO);
        EventStream stream = bridge.bridge(SPONGE_NEO_KEY);
        assertEquals(PlatformFamily.SPONGE_NEO, bridge.family());
        assertEquals(5, bridge.listenerOrder().size());
        assertTrue(bridge.coreTypeCount(stream) >= 10, "核心事件类型不足 10 类");
        assertFalse(stream.ofType(EventType.REGISTRY).isEmpty(), "缺少注册表事件");
        assertTrue(stream.ofType(EventType.GAME_TEST_SUITE).isEmpty(), "Sponge 无 GameTest，不应产出套件事件");
    }

    @Test
    void classifierPrefersSpongeDescriptorOverLoaderMetadata() {
        SpongeJarClassifier classifier = new SpongeJarClassifier();
        // SpongeForge：同一 JAR 两者都有，先命中的是 Sponge。
        assertEquals(SpongeJarClassifier.JarKind.SPONGE, classifier.classify(
                List.of("META-INF/mods.toml", "META-INF/sponge_plugins.json")));
        assertEquals(SpongeJarClassifier.JarKind.NEOFORGE_MOD, classifier.classify(
                List.of("META-INF/neoforge.mods.toml")));
        assertEquals(SpongeJarClassifier.JarKind.FORGE_MOD, classifier.classify(
                List.of("META-INF/mods.toml")));
        assertEquals(SpongeJarClassifier.JarKind.UNKNOWN, classifier.classify(List.of("plugin.yml")));
        assertEquals(List.of("META-INF/sponge_plugins.json", "META-INF/neoforge.mods.toml",
                "META-INF/mods.toml"), SpongeJarClassifier.precedence());
    }

    @Test
    void spongeAdaptersResolveExactly() {
        AdapterRegistry registry = AdapterRegistryFactory.defaultRegistry();
        for (AbstractLaplaceAdapter adapter : SPONGE) {
            PlatformKey key = AdapterTestSupport.keyFor(adapter);
            Optional<LaplaceAdapter> resolved = registry.resolve(key);
            assertTrue(resolved.isPresent(), "未解析到: " + key.id());
            assertEquals(adapter.id(), resolved.get().id());
        }
    }

    @Test
    void spongeCompatChainEmitsWarning() {
        // SpongeForge 版本不在区间 → 回退 Forge。
        assertFallback(PlatformKey.of(PlatformFamily.SPONGE_FORGE, "1.19.2"), "forge");
        // SpongeNeo 的版本线对不上（拿到的是 NeoForge 加载器版本）→ 回退 NeoForge。
        assertFallback(new PlatformKey(PlatformFamily.SPONGE_NEO, "1.21.10", "21.11.0"), "neoforge");
    }

    @Test
    void spongeVanillaHasNoFallbackTarget() {
        AdapterRegistry registry = AdapterRegistryFactory.defaultRegistry();
        Optional<LaplaceAdapter> resolved = registry.resolve(PlatformKey.of(PlatformFamily.SPONGE_VANILLA, "1.19.2"));
        assertTrue(resolved.isEmpty());
        assertTrue(registry.warnings().stream().anyMatch(warning -> warning.contains("无可用适配器")));
    }

    @Test
    void spongeAdaptersPassSmokeScenarios() {
        SmokeScenarios smoke = new SmokeScenarios();
        List<SmokeScenarios.SmokeResult> results = SPONGE.stream()
                .flatMap(adapter -> smoke.run(adapter, AdapterTestSupport.keyFor(adapter)).stream())
                .toList();
        assertEquals(SPONGE.size() * 3, results.size());
        List<SmokeScenarios.SmokeResult> failures = results.stream().filter(r -> !r.passed()).toList();
        assertTrue(failures.isEmpty(), "Sponge 冒烟未通过: " + failures);
    }

    @Test
    void resolvesSpongeNeoAtMainLineVersion() {
        Optional<LaplaceAdapter> resolved = AdapterRegistryFactory.defaultRegistry()
                .resolve(PlatformKey.of(PlatformFamily.SPONGE_NEO, "1.21.10"));
        assertTrue(resolved.isPresent(), "主线 API 17 / MC 1.21.10 未解析到适配器");
        assertEquals("spongeneo", resolved.get().id());
        List<SmokeScenarios.SmokeResult> results =
                new SmokeScenarios().run(resolved.get(), PlatformKey.of(PlatformFamily.SPONGE_NEO, "1.21.10"));
        assertTrue(results.stream().allMatch(SmokeScenarios.SmokeResult::passed), results.toString());
    }

    private static void assertFallback(PlatformKey key, String expectedAdapterId) {
        AdapterRegistry registry = AdapterRegistryFactory.defaultRegistry();
        Optional<LaplaceAdapter> resolved = registry.resolve(key);
        assertTrue(resolved.isPresent(), "回退失败: " + key.id());
        assertEquals(expectedAdapterId, resolved.get().id());
        assertTrue(registry.warnings().stream().anyMatch(warning -> warning.contains("回退到")),
                "回退必须输出降级告警: " + registry.warnings());
    }
}
