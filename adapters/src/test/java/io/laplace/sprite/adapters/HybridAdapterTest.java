package io.laplace.sprite.adapters;

import io.laplace.sprite.adapters.hybrid.AbstractHybridAdapter;
import io.laplace.sprite.adapters.hybrid.ArclightAdapter;
import io.laplace.sprite.adapters.hybrid.ArclightVariant;
import io.laplace.sprite.adapters.hybrid.CardboardAdapter;
import io.laplace.sprite.adapters.hybrid.CrossEcosystemIdMapper;
import io.laplace.sprite.adapters.hybrid.HybridEventBridge;
import io.laplace.sprite.adapters.hybrid.HybridEventDeduplicator;
import io.laplace.sprite.adapters.hybrid.HybridInstallLayout;
import io.laplace.sprite.adapters.hybrid.HybridPitfalls;
import io.laplace.sprite.adapters.hybrid.MagmaNeoAdapter;
import io.laplace.sprite.adapters.hybrid.YouerAdapter;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.model.Artifact;
import io.laplace.sprite.core.model.ArtifactDependency;
import io.laplace.sprite.core.model.ArtifactRole;
import io.laplace.sprite.core.model.ArtifactSource;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.spi.AdapterRegistry;
import io.laplace.sprite.core.spi.LaplaceAdapter;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 混合端接入（14.3 / 14.9）：组合适配层三件事 + 已知坑 + 冒烟。 */
class HybridAdapterTest {

    private static final List<AbstractHybridAdapter> HYBRIDS = List.of(
            new ArclightAdapter(), new YouerAdapter(), new MagmaNeoAdapter(), new CardboardAdapter());

    private static final PlatformKey ARCLIGHT_KEY = PlatformKey.of(PlatformFamily.ARCLIGHT, "1.21.1");

    @Test
    void hybridAdaptersReportKnownPitfalls() {
        for (AbstractHybridAdapter adapter : HYBRIDS) {
            List<String> notes = adapter.testAdaptationNotes();
            assertTrue(notes.containsAll(HybridPitfalls.known()),
                    adapter.id() + " 未如实列出已知坑");
            for (String pitfall : List.of("事件双发", "类阴影", "命令树", "原版语义")) {
                assertTrue(notes.stream().anyMatch(note -> note.contains(pitfall)),
                        adapter.id() + " 缺少坑: " + pitfall);
            }
        }
    }

    @Test
    void arclightModelsThreeVariantsAsData() {
        assertEquals(3, ArclightAdapter.variants().size());
        assertEquals(List.of(ArclightVariant.FORGE, ArclightVariant.NEOFORGE, ArclightVariant.FABRIC),
                ArclightAdapter.variants());
        assertEquals(PlatformFamily.FORGE, new ArclightAdapter().variant().loaderFamily());
        assertEquals(PlatformFamily.NEOFORGE,
                new ArclightAdapter(ArclightVariant.NEOFORGE).variant().loaderFamily());
        assertEquals("arclight-fabric", new ArclightAdapter(ArclightVariant.FABRIC).id());
    }

    @Test
    void hybridInstallLayoutServesBothDirectories() {
        HybridInstallLayout layout = new HybridInstallLayout();
        assertEquals(Path.of("server", "plugins"), layout.pluginDirectory());
        assertEquals(Path.of("server", "mods"), layout.modDirectory());
        assertEquals(List.of(Path.of("server", "plugins"), Path.of("server", "mods")), layout.directories());
        assertEquals(Path.of("server"), layout.layoutDirectory(ARCLIGHT_KEY));
    }

    @Test
    void hybridInstallLayoutMergesAcrossEcosystems() {
        HybridInstallLayout layout = new HybridInstallLayout();
        Artifact plugin = artifact("plugin-core", ArtifactRole.PLUGIN, List.of(ArtifactDependency.hard("mod-core")));
        Artifact mod = artifact("mod-core", ArtifactRole.MOD, List.of());
        List<String> ordered = layout.mergeAcrossEcosystems(List.of(plugin), List.of(mod)).stream()
                .map(Artifact::id)
                .toList();
        assertEquals(List.of("mod-core", "plugin-core"), ordered, "跨生态硬依赖必须排序在前");
    }

    @Test
    void hybridEventBridgeCollapsesDoubleFiredEvents() {
        HybridEventBridge bridge = new HybridEventBridge(PlatformFamily.SPIGOT, PlatformFamily.FORGE);
        EventStream stream = bridge.bridge(ARCLIGHT_KEY);
        assertTrue(bridge.lastDeduplicatedCount() > 0, "双发事件未被折叠");
        assertTrue(stream.stream().map(Event::type).distinct().count() >= 10, "合并后核心事件类型不足");
        assertEquals(PlatformFamily.SPIGOT, bridge.bukkitFamily());
        assertEquals(PlatformFamily.FORGE, bridge.loaderFamily());
    }

    @Test
    void deduplicatorCollapsesDuplicatesButKeepsDistinctEvents() {
        PlatformKey key = PlatformKey.of(PlatformFamily.ARCLIGHT, "1.21.1");
        Event first = new Event.Chat("p", 5, AdapterTestSupport.ORIGIN, key, "hello");
        Event duplicate = new Event.Chat("p", 5, AdapterTestSupport.ORIGIN, key, "hello");
        Event distinct = new Event.Chat("p", 5, AdapterTestSupport.ORIGIN, key, "world");

        HybridEventDeduplicator.Result result =
                new HybridEventDeduplicator().deduplicate(List.of(first, duplicate, distinct));
        assertEquals(2, result.events().size(), "同载荷重复应折叠，载荷不同的事件应保留");
        assertEquals(1, result.dropped());
    }

    @Test
    void crossEcosystemIdMapperCleanCase() {
        CrossEcosystemIdMapper mapper = CrossEcosystemIdMapper.defaultMapper();
        List<String> ids = List.of("minecraft:stone", "minecraft:iron_ingot", "minecraft:zombie",
                "minecraft:ender_dragon");
        assertTrue(mapper.validate(ids).isEmpty(), "已登记映射不应报违规");
        assertTrue(mapper.audit().isEmpty(), "内置映射表不应有歧义登记");
        assertTrue(mapper.report(ids).clean());
        assertEquals(4, mapper.report(ids).mapped());
        assertTrue(mapper.countsByKind().get(CrossEcosystemIdMapper.TargetKind.ITEM) > 0);
        assertTrue(mapper.countsByKind().get(CrossEcosystemIdMapper.TargetKind.ENTITY) > 0);
    }

    @Test
    void crossEcosystemIdMapperViolationCase() {
        CrossEcosystemIdMapper mapper = CrossEcosystemIdMapper.defaultMapper();
        List<CrossEcosystemIdMapper.MappingViolation> violations =
                mapper.validate(List.of("minecraft:stone", "example:custom_block"));
        assertEquals(1, violations.size());
        assertEquals(CrossEcosystemIdMapper.Code.UNMAPPED, violations.get(0).code());
        assertEquals("example:custom_block", violations.get(0).registryId());
        assertFalse(mapper.report(List.of("example:custom_block")).clean());

        // 同一注册 id 对到两个 Bukkit 目标即歧义。
        CrossEcosystemIdMapper ambiguous = new CrossEcosystemIdMapper(List.of(
                new CrossEcosystemIdMapper.IdMapping("minecraft:stone", "STONE",
                        CrossEcosystemIdMapper.TargetKind.ITEM),
                new CrossEcosystemIdMapper.IdMapping("minecraft:stone", "SMOOTH_STONE",
                        CrossEcosystemIdMapper.TargetKind.ITEM)));
        assertFalse(ambiguous.audit().isEmpty());
        assertEquals(CrossEcosystemIdMapper.Code.AMBIGUOUS,
                ambiguous.validate(List.of("minecraft:stone")).get(0).code());
    }

    @Test
    void hybridCompatChainFallsBackToPureFamily() {
        // Arclight 版本不在区间 → 回退纯 Bukkit（Paper）。
        assertFallback(PlatformKey.of(PlatformFamily.ARCLIGHT, "1.19.4"), "paper");
        // Magma-Neo 版本不在区间 → 回退纯模组端（NeoForge）。
        assertFallback(PlatformKey.of(PlatformFamily.MAGMA_NEO, "1.20.4"), "neoforge");
    }

    @Test
    void cardboardIsCompatShell() {
        assertEquals(AdapterStatus.COMPAT_SHELL, new CardboardAdapter().adapterStatus());
    }

    @Test
    void resolvesArclightAt1211AndPassesSmoke() {
        Optional<LaplaceAdapter> resolved = AdapterRegistryFactory.defaultRegistry().resolve(ARCLIGHT_KEY);
        assertTrue(resolved.isPresent(), "Arclight 1.21.1 未解析到适配器");
        assertEquals("arclight", resolved.get().id());
        List<SmokeScenarios.SmokeResult> results = new SmokeScenarios().run(resolved.get(), ARCLIGHT_KEY);
        assertEquals(3, results.size());
        assertTrue(results.stream().allMatch(SmokeScenarios.SmokeResult::passed), results.toString());
    }

    @Test
    void hybridAdaptersResolveExactlyAndPassSmoke() {
        AdapterRegistry registry = AdapterRegistryFactory.defaultRegistry();
        SmokeScenarios smoke = new SmokeScenarios();
        for (AbstractHybridAdapter adapter : HYBRIDS) {
            PlatformKey key = AdapterTestSupport.keyFor(adapter);
            Optional<LaplaceAdapter> resolved = registry.resolve(key);
            assertTrue(resolved.isPresent(), "未解析到: " + key.id());
            assertEquals(adapter.id(), resolved.get().id());

            List<SmokeScenarios.SmokeResult> results = smoke.run(adapter, key);
            List<SmokeScenarios.SmokeResult> failures = results.stream().filter(r -> !r.passed()).toList();
            assertTrue(failures.isEmpty(), adapter.id() + " 冒烟未通过: " + failures);
        }
    }

    private static void assertFallback(PlatformKey key, String expectedAdapterId) {
        AdapterRegistry registry = AdapterRegistryFactory.defaultRegistry();
        Optional<LaplaceAdapter> resolved = registry.resolve(key);
        assertTrue(resolved.isPresent(), "回退失败: " + key.id());
        assertEquals(expectedAdapterId, resolved.get().id());
        assertTrue(registry.warnings().stream().anyMatch(warning -> warning.contains("回退到")),
                "回退必须输出降级告警: " + registry.warnings());
    }

    private static Artifact artifact(String id, ArtifactRole role, List<ArtifactDependency> dependencies) {
        return new Artifact(id, id, "1.0.0", ArtifactSource.MAVEN, "io.laplace.test:" + id + ":1.0.0", null,
                role, dependencies, Map.of());
    }
}
