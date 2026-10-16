package io.laplace.sprite.adapters;

import io.laplace.sprite.adapters.descriptor.BungeeYmlParser;
import io.laplace.sprite.adapters.descriptor.DescriptorParserRegistry;
import io.laplace.sprite.adapters.descriptor.FabricModJsonParser;
import io.laplace.sprite.adapters.descriptor.ForgeModsTomlParser;
import io.laplace.sprite.adapters.descriptor.NeoForgeModsTomlParser;
import io.laplace.sprite.adapters.descriptor.PaperPluginYmlParser;
import io.laplace.sprite.adapters.descriptor.PluginDescriptor;
import io.laplace.sprite.adapters.descriptor.PluginYmlParser;
import io.laplace.sprite.adapters.descriptor.QuiltModJsonParser;
import io.laplace.sprite.adapters.descriptor.SpongePluginsJsonParser;
import io.laplace.sprite.adapters.descriptor.VelocityDescriptorParser;
import io.laplace.sprite.core.model.ArtifactDependency;
import io.laplace.sprite.core.model.DependencySemantics;
import io.laplace.sprite.core.model.PlatformFamily;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 描述符解析（14.2 / 14.3）：每个格式都有真实样本。 */
class DescriptorParserTest {

    private static final String PLUGIN_YML = """
            name: LaplaceProbe
            main: io.laplace.sprite.probe.LaplaceProbePlugin
            version: 1.2.3
            api-version: "1.21"
            load: POSTWORLD
            depend: [ProtocolLib]
            softdepend:
              - PlaceholderAPI
              - LuckPerms
            loadbefore: [LegacyBridge]
            folia-supported: true
            """;

    private static final String NUKKIT_PLUGIN_YML = """
            name: BedrockProbe
            main: io.laplace.sprite.probe.BedrockProbePlugin
            version: 1.0.0
            api: ["1.0.0", "1.0.1"]
            depend: [NukkitRedis]
            """;

    private static final String PAPER_PLUGIN_YML = """
            name: PaperProbe
            main: io.laplace.sprite.probe.PaperProbePlugin
            version: 2.0.0
            api-version: "1.21"
            folia-supported: true
            dependencies:
              server:
                Vault:
                  required: true
                  version: "[1.7,)"
                PlaceholderAPI:
                  required: false
            """;

    private static final String BUNGEE_YML = """
            name: BungeeProbe
            main: io.laplace.sprite.probe.BungeeProbe
            version: 1.0.0
            author: laplace
            depend: [RedisBungee]
            softdepend: [LuckPerms]
            """;

    private static final String VELOCITY_JSON = """
            {
              "id": "velocityprobe",
              "name": "VelocityProbe",
              "version": "1.0.0",
              "main": "io.laplace.sprite.probe.VelocityProbe",
              "dependencies": [
                { "id": "luckperms", "optional": true },
                { "id": "protocolize", "optional": false }
              ]
            }
            """;

    private static final String ALLAY_JSON = """
            {
              "name": "AllayProbe",
              "entrance": "io.laplace.sprite.probe.AllayProbe",
              "authors": ["laplace"],
              "version": "1.0.0",
              "dependencies": [
                { "name": "allay-economy", "version": "^1.0.0", "optional": true },
                "allay-core"
              ]
            }
            """;

    private static final String FABRIC_JSON = """
            {
              "schemaVersion": 1,
              "id": "fabricprobe",
              "version": "1.0.0",
              "name": "Fabric Probe",
              "environment": "server",
              "entrypoints": { "main": ["io.laplace.sprite.probe.FabricProbe"] },
              "depends": { "fabricloader": ">=0.15.0", "minecraft": "~1.21", "fabric-api": "*" },
              "recommends": { "modmenu": "*" },
              "suggests": { "jade": "*" },
              "breaks": { "badmod": "*" },
              "conflicts": { "othermod": "*" }
            }
            """;

    private static final String QUILT_JSON = """
            {
              "schema_version": 1,
              "quilt_loader": {
                "group": "io.laplace.sprite",
                "id": "quiltprobe",
                "version": "1.0.0",
                "metadata": { "name": "Quilt Probe" },
                "entrypoints": { "init": ["io.laplace.sprite.probe.QuiltProbe::init"] },
                "depends": [
                  { "id": "quilt_loader", "versions": ">=0.26.0" },
                  { "id": "minecraft", "versions": ">=1.21", "optional": false }
                ]
              },
              "mixin": ["quiltprobe.mixins.json"]
            }
            """;

    private static final String FORGE_TOML = """
            modLoader="javafml"
            loaderVersion="[61,)"
            license="MIT"

            [[mods]]
            modId="forgeprobe"
            version="1.0.0"
            displayName="Forge Probe"

            [[dependencies.forgeprobe]]
            modId="forge"
            mandatory=true
            versionRange="[61,)"
            ordering="NONE"
            side="BOTH"

            [[dependencies.forgeprobe]]
            modId="minecraft"
            mandatory=true
            versionRange="[1.21.11,1.22)"
            ordering="NONE"
            side="BOTH"

            [[dependencies.forgeprobe]]
            modId="jei"
            mandatory=false
            versionRange="[15,)"
            ordering="AFTER"
            side="CLIENT"
            """;

    private static final String NEOFORGE_TOML = """
            modLoader="javafml"
            loaderVersion="[21.11,)"

            [[mods]]
            modId="neoprobe"
            version="2.1.0"
            displayName="Neo Probe"

            [[dependencies.neoprobe]]
            modId="neoforge"
            mandatory=true
            versionRange="[21.11,)"
            ordering="NONE"
            side="BOTH"
            """;

    private static final String SPONGE_PLUGINS_JSON = """
            {
              "loader": { "name": "java_plain", "version": "1.0" },
              "plugins": [
                {
                  "id": "spongeprobe",
                  "name": "Sponge Probe",
                  "version": "1.0.0",
                  "entrypoint": "io.laplace.sprite.probe.SpongeProbe",
                  "description": "probe plugin",
                  "dependencies": [
                    { "id": "spongeapi", "version": "17.0.0", "load-order": "BEFORE", "optional": false },
                    { "id": "luckperms", "version": "5.4", "load-order": "AFTER", "optional": true }
                  ]
                }
              ]
            }
            """;

    @Test
    void parsesSpongePluginsJson() {
        PluginDescriptor descriptor = new SpongePluginsJsonParser().parse(SPONGE_PLUGINS_JSON);
        assertEquals("spongeprobe", descriptor.id());
        assertEquals("Sponge Probe", descriptor.name());
        assertEquals("io.laplace.sprite.probe.SpongeProbe", descriptor.mainClass());
        assertEquals("1.0.0", descriptor.version());
        assertEquals(2, descriptor.dependencies().size());
        // load-order BEFORE 记成反向边，AFTER + optional 记成软依赖。
        assertEquals(DependencySemantics.REVERSE, descriptor.dependencies().get(0).semantics());
        assertEquals(DependencySemantics.SOFT, descriptor.dependencies().get(1).semantics());
        assertEquals("java_plain", descriptor.rawFields().get("loaderName"));
        assertEquals("BEFORE", descriptor.rawFields().get("loadOrder"));
    }

    @Test
    void parsesBukkitPluginYml() {
        PluginDescriptor descriptor = new PluginYmlParser().parse(PLUGIN_YML);
        assertEquals("LaplaceProbe", descriptor.id());
        assertEquals("io.laplace.sprite.probe.LaplaceProbePlugin", descriptor.mainClass());
        assertEquals("1.2.3", descriptor.version());
        assertEquals(List.of("1.21"), descriptor.apiVersions());
        assertEquals("POSTWORLD", descriptor.loadPhase());
        assertTrue(descriptor.foliaSupported());
        assertEquals("server", descriptor.side());
        assertEquals(1, descriptorsWith(descriptor, DependencySemantics.HARD).size());
        assertEquals(2, descriptorsWith(descriptor, DependencySemantics.SOFT).size());
        assertTrue(descriptorsWith(descriptor, DependencySemantics.REVERSE).stream()
                .anyMatch(dependency -> dependency.id().equals("LegacyBridge")));
    }

    @Test
    void parsesNukkitApiVersionList() {
        PluginDescriptor descriptor = new PluginYmlParser().parse(NUKKIT_PLUGIN_YML);
        assertEquals("BedrockProbe", descriptor.id());
        assertEquals(List.of("1.0.0", "1.0.1"), descriptor.apiVersions());
        assertFalse(descriptor.foliaSupported());
        assertEquals("NukkitRedis", descriptor.dependencies().get(0).id());
    }

    @Test
    void parsesPaperPluginYmlWithDependencySections() {
        PluginDescriptor descriptor = new PaperPluginYmlParser().parse(PAPER_PLUGIN_YML);
        assertEquals("PaperProbe", descriptor.id());
        assertTrue(descriptor.foliaSupported());
        List<ArtifactDependency> hard = descriptorsWith(descriptor, DependencySemantics.HARD);
        List<ArtifactDependency> soft = descriptorsWith(descriptor, DependencySemantics.SOFT);
        assertEquals(1, hard.size());
        assertEquals("Vault", hard.get(0).id());
        assertEquals("[1.7,)", hard.get(0).range());
        assertEquals(1, soft.size());
        assertEquals("PlaceholderAPI", soft.get(0).id());
        assertTrue(soft.get(0).optional());
    }

    @Test
    void parsesBungeeYml() {
        PluginDescriptor descriptor = new BungeeYmlParser().parse(BUNGEE_YML);
        assertEquals("BungeeProbe", descriptor.id());
        assertEquals("proxy", descriptor.side());
        assertEquals(1, descriptorsWith(descriptor, DependencySemantics.HARD).size());
        assertEquals(1, descriptorsWith(descriptor, DependencySemantics.SOFT).size());
    }

    @Test
    void parsesVelocityDescriptor() {
        PluginDescriptor descriptor = new VelocityDescriptorParser().parse(VELOCITY_JSON);
        assertEquals("velocityprobe", descriptor.id());
        assertEquals("io.laplace.sprite.probe.VelocityProbe", descriptor.mainClass());
        assertEquals(1, descriptorsWith(descriptor, DependencySemantics.HARD).size());
        assertEquals(1, descriptorsWith(descriptor, DependencySemantics.SOFT).size());
        assertTrue(descriptorsWith(descriptor, DependencySemantics.SOFT).get(0).optional());
    }

    @Test
    void parsesAllayPluginJson() {
        PluginDescriptor descriptor = new io.laplace.sprite.adapters.descriptor.AllayPluginJsonParser()
                .parse(ALLAY_JSON);
        assertEquals("AllayProbe", descriptor.id());
        assertEquals("io.laplace.sprite.probe.AllayProbe", descriptor.mainClass());
        assertEquals("1.0.0", descriptor.version());
        assertEquals(2, descriptor.dependencies().size());
        assertEquals("^1.0.0", descriptor.dependencies().get(0).range());
        assertTrue(descriptor.dependencies().get(0).optional());
    }

    @Test
    void parsesFabricModJsonFiveLevels() {
        PluginDescriptor descriptor = new FabricModJsonParser().parse(FABRIC_JSON);
        assertEquals("fabricprobe", descriptor.id());
        assertEquals("io.laplace.sprite.probe.FabricProbe", descriptor.mainClass());
        assertEquals("server", descriptor.side());
        assertEquals("server", descriptor.environment());
        assertEquals(3, descriptorsWith(descriptor, DependencySemantics.HARD).size());
        assertEquals(2, descriptorsWith(descriptor, DependencySemantics.SOFT).size());
        assertEquals(2, descriptorsWith(descriptor, DependencySemantics.REVERSE).size());
        assertEquals("1", descriptor.rawFields().get("schemaVersion"));
    }

    @Test
    void parsesQuiltModJson() {
        PluginDescriptor descriptor = new QuiltModJsonParser().parse(QUILT_JSON);
        assertEquals("io.laplace.sprite:quiltprobe", descriptor.id());
        assertEquals("io.laplace.sprite.probe.QuiltProbe", descriptor.mainClass());
        assertEquals("Quilt Probe", descriptor.name());
        assertEquals(2, descriptorsWith(descriptor, DependencySemantics.HARD).size());
        assertEquals("quiltprobe.mixins.json", descriptor.rawFields().get("mixin"));
    }

    @Test
    void parsesForgeModsToml() {
        PluginDescriptor descriptor = new ForgeModsTomlParser().parse(FORGE_TOML);
        assertEquals("forgeprobe", descriptor.id());
        assertEquals("Forge Probe", descriptor.name());
        assertEquals(List.of("[61,)"), descriptor.apiVersions());
        assertEquals(2, descriptorsWith(descriptor, DependencySemantics.HARD).size());
        assertEquals(1, descriptorsWith(descriptor, DependencySemantics.SOFT).size());
        assertEquals("[61,)", descriptor.rawFields().get("loaderVersion"));
        assertEquals("NONE", descriptor.rawFields().get("ordering"));
    }

    @Test
    void parsesNeoForgeModsToml() {
        PluginDescriptor descriptor = new NeoForgeModsTomlParser().parse(NEOFORGE_TOML);
        assertEquals("neoprobe", descriptor.id());
        assertEquals(List.of("[21.11,)"), descriptor.apiVersions());
        assertEquals(1, descriptor.dependencies().size());
        assertEquals("neoforge", descriptor.dependencies().get(0).id());
    }

    @Test
    void registryResolvesAndRejectsFamilies() {
        DescriptorParserRegistry registry = new DescriptorParserRegistry();
        assertEquals("paper-plugin.yml", registry.parserFor(PlatformFamily.PAPER).format());
        assertEquals("plugin.yml", registry.parserFor(PlatformFamily.NUKKIT).format());
        assertEquals("bungee.yml", registry.parserFor(PlatformFamily.WATERFALL).format());
        assertEquals("velocity-plugin.json", registry.parserFor(PlatformFamily.VELOCITY).format());
        assertEquals("plugin.json", registry.parserFor(PlatformFamily.ALLAY).format());
        assertEquals("fabric.mod.json", registry.parserFor(PlatformFamily.FABRIC).format());
        assertEquals("quilt.mod.json", registry.parserFor(PlatformFamily.QUILT).format());
        assertEquals("META-INF/mods.toml", registry.parserFor(PlatformFamily.FORGE).format());
        assertEquals("META-INF/neoforge.mods.toml", registry.parserFor(PlatformFamily.NEOFORGE).format());
        assertEquals("META-INF/sponge_plugins.json", registry.parserFor(PlatformFamily.SPONGE_VANILLA).format());
        assertEquals("META-INF/sponge_plugins.json", registry.parserFor(PlatformFamily.SPONGE_NEO).format());
        assertEquals("META-INF/sponge_plugins.json", registry.parserFor(PlatformFamily.SPONGE_FORGE).format());

        // Geyser 是挂载在宿主上的插件，复用 plugin.yml 解析。
        assertTrue(registry.supports(PlatformFamily.GEYSER));
        assertFalse(registry.supports(PlatformFamily.MODPACK));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> registry.parserFor(PlatformFamily.MODPACK));
        assertTrue(error.getMessage().contains("MODPACK"));
        assertThrows(IllegalArgumentException.class,
                () -> registry.parse(PlatformFamily.VANILLA_DATAPACK, "{}"));
    }

    private static List<ArtifactDependency> descriptorsWith(PluginDescriptor descriptor,
                                                            DependencySemantics semantics) {
        return descriptor.dependencies().stream()
                .filter(dependency -> dependency.semantics() == semantics)
                .toList();
    }
}
