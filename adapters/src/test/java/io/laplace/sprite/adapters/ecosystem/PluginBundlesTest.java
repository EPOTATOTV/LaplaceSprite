package io.laplace.sprite.adapters.ecosystem;

import io.laplace.sprite.core.model.PlatformFamily;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 内容型模组、热门插件与重型插件共存包（14.9）。 */
class PluginBundlesTest {

    @Test
    void topPluginLoadOrderIsExact() {
        TopPluginBundle bom = TopPluginBundle.bom();

        assertEquals(List.of("Vault", "LuckPerms", "PlaceholderAPI", "EssentialsX"), bom.loadOrder());
        assertTrue(bom.loadOrderMatchesBom());
        assertEquals(List.of("hangar:Vault", "hangar:LuckPerms", "hangar:PlaceholderAPI",
                        "hangar:EssentialsX"),
                bom.artifacts().stream().map(TopPluginBundle.PluginArtifact::coordinate).toList());
    }

    @Test
    void topPluginBundleHasFourAssertionLayers() {
        TopPluginBundle bom = TopPluginBundle.bom();

        assertEquals(List.of("startup", "commands", "permission", "placeholders"),
                bom.assertionLayers().stream().map(TopPluginBundle.AssertionLayer::id).toList());
        List<String> statements = bom.assertions().stream()
                .map(BundleAssertion::statement).toList();
        assertTrue(statements.stream().anyMatch(line -> line.contains("ConflictingPlugin")));
        assertTrue(statements.stream().anyMatch(line -> line.contains("NoClassDefFoundError")));
        assertTrue(statements.stream().anyMatch(line -> line.contains("命令重复注册")));
        assertTrue(statements.stream().anyMatch(line -> line.contains("/balance")));
        assertTrue(statements.stream().anyMatch(line -> line.contains("/lp user")));
        assertTrue(statements.stream().anyMatch(line -> line.contains("/pl")));
        assertTrue(statements.stream().anyMatch(line -> line.contains("/papi parse")));
        assertTrue(statements.stream().anyMatch(line -> line.contains("essentials.spawn")));
        assertTrue(statements.stream().anyMatch(line -> line.contains("%essentials_balance%")));
        assertTrue(statements.stream().anyMatch(line -> line.contains("%luckperms_prefix%")));
    }

    @Test
    void coexistenceBundleUsesPerLoaderArtifacts() {
        CoexistenceBundle fabric = CoexistenceBundle.forLoader(PlatformFamily.FABRIC);
        assertEquals(List.of("modrinth:create-fabric", "modrinth:alexs-mobs-continued"),
                fabric.coordinates());
        assertEquals(List.of("fabric", "fabric"),
                fabric.artifacts().stream().map(CoexistenceBundle.ModArtifact::loader).toList());

        CoexistenceBundle forge = CoexistenceBundle.forLoader(PlatformFamily.FORGE);
        assertEquals(List.of("modrinth:create", "modrinth:alexs-mobs-continued"),
                forge.coordinates());
        assertEquals(List.of("modrinth:create", "modrinth:alexs-mobs-continued"),
                CoexistenceBundle.forLoader(PlatformFamily.NEOFORGE).coordinates());
    }

    @Test
    void coexistenceBundleCarriesTheFourAssertionsAndControlCells() {
        CoexistenceBundle bundle = CoexistenceBundle.forLoader(PlatformFamily.FABRIC);

        List<String> statements = bundle.assertions().stream().map(BundleAssertion::statement).toList();
        assertTrue(statements.stream().anyMatch(line -> line.contains("duplicate registry entry")));
        assertTrue(statements.stream().anyMatch(line -> line.contains("missing dependency")));
        assertTrue(statements.stream().anyMatch(line -> line.contains("/summon Alex")));
        assertTrue(statements.stream().anyMatch(line -> line.contains("Create 机械")));
        assertTrue(statements.stream().anyMatch(line -> line.contains("5 分钟")));

        List<CoexistenceBundle.ControlCell> controls = bundle.controlCells();
        assertEquals(2, controls.size());
        assertTrue(controls.stream().allMatch(cell -> cell.coordinates().size() == 1));
        assertFalse(CoexistenceBundle.attributionRule().isBlank());
    }

    @Test
    void heavyPluginBundleCoversWorldsEditingAndProtocol() {
        HeavyPluginBundle bundle = HeavyPluginBundle.forFamily(PlatformFamily.PAPER);

        assertEquals(List.of("Multiverse-Core", "WorldEdit", "ViaVersion"), bundle.loadOrder());
        List<String> statements = bundle.assertions().stream().map(BundleAssertion::statement).toList();
        assertTrue(statements.stream().anyMatch(line -> line.contains("/mv create")));
        assertTrue(statements.stream().anyMatch(line -> line.contains("//set")));
        assertTrue(statements.stream().anyMatch(line -> line.contains("跨世界")));
        assertTrue(statements.stream().anyMatch(line -> line.contains("协议降级")));
        assertThrows(IllegalArgumentException.class,
                () -> HeavyPluginBundle.forFamily(PlatformFamily.VELOCITY));
    }
}
