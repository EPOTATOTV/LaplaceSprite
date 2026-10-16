package io.laplace.sprite.adapters;

import io.laplace.sprite.adapters.artifact.ArtifactResolverFactory;
import io.laplace.sprite.adapters.artifact.CurseForgeClient;
import io.laplace.sprite.adapters.artifact.MavenArtifactResolver;
import io.laplace.sprite.adapters.artifact.ModrinthClient;
import io.laplace.sprite.adapters.artifact.PackwizResolver;
import io.laplace.sprite.core.model.Artifact;
import io.laplace.sprite.core.model.ArtifactRole;
import io.laplace.sprite.core.model.ArtifactSource;
import io.laplace.sprite.core.model.ArtifactSpec;
import io.laplace.sprite.core.spi.ArtifactResolver;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 产物解析（14.5）：Maven / Modrinth / CurseForge / packwiz，全部离线可跑。 */
class ArtifactResolutionTest {

    private static final URI UNREACHABLE = URI.create("http://127.0.0.1:1/api");

    private static final String PACK_TOML = """
            name = "Laplace Probe Pack"
            pack-format = "packwiz:1.1.0"

            [versions]
            minecraft = "1.21.11"
            fabric = "0.19.5"

            [index]
            file = "index.toml"
            hash-format = "sha256"
            hash = "0000"
            """;

    private static final String INDEX_TOML = """
            hash-format = "sha256"

            [[files]]
            file = "mods/fabric-api.pw.toml"
            hash = "1111"

            [[files]]
            file = "mods/sodium.pw.toml"
            hash = "2222"

            [[files]]
            file = "mods/iris.pw.toml"
            hash = "3333"
            """;

    private static final String FABRIC_API_PW_TOML = """
            name = "Fabric API"
            filename = "fabric-api-0.100.0.jar"
            side = "both"

            [download]
            url = "https://example.invalid/fabric-api.jar"
            hash-format = "sha512"
            hash = "aaaa"

            [update.modrinth]
            mod-id = "P7dR8mSH"
            version = "0.100.0+1.21.11"
            """;

    private static final String SODIUM_PW_TOML = """
            name = "Sodium"
            filename = "sodium.jar"
            side = "client"

            [download]
            url = "https://example.invalid/sodium.jar"
            """;

    private static final String IRIS_PW_TOML = """
            name = "Iris"
            filename = "iris.jar"
            side = "both"

            [option]
            optional = true

            [download]
            url = "https://example.invalid/iris.jar"
            """;

    @Test
    void parsesMavenCoordinates() {
        Artifact artifact = new MavenArtifactResolver()
                .resolve(ArtifactSpec.maven("io.laplace.sprite:sprite-probe:1.0.0", ArtifactRole.PLUGIN));
        assertEquals("io.laplace.sprite:sprite-probe", artifact.id());
        assertEquals("sprite-probe", artifact.name());
        assertEquals("1.0.0", artifact.version());
        assertEquals(ArtifactSource.MAVEN, artifact.source());
        assertEquals(ArtifactRole.PLUGIN, artifact.role());
        assertTrue(artifact.dependencies().isEmpty(), "Maven 侧不解析传递依赖");
    }

    @Test
    void rejectsMalformedMavenCoordinate() {
        MavenArtifactResolver resolver = new MavenArtifactResolver();
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(ArtifactSpec.maven("only:two", ArtifactRole.PLUGIN)));
    }

    @Test
    void modrinthFallsBackToOfflineSnapshot() {
        ModrinthClient client = new ModrinthClient(UNREACHABLE, null, false);
        Artifact artifact = client.resolve(ArtifactSpec.modrinth("fabric-api", "0.100.0+1.21.11",
                ArtifactRole.MOD));
        assertEquals("Fabric API", artifact.name());
        assertEquals("0.100.0+1.21.11", artifact.version());
        assertEquals("true", artifact.metadata().get("offline"));
        assertEquals(1, artifact.dependencies().size());
        assertEquals("fabricloader", artifact.dependencies().get(0).id());
    }

    @Test
    void modrinthCompletesClosureAtApiLayer() {
        ModrinthClient client = new ModrinthClient(UNREACHABLE, null, false);
        Artifact artifact = client.resolve(ArtifactSpec.modrinth("fabric-api", "0.100.0+1.21.11",
                ArtifactRole.MOD));
        List<Artifact> closure = client.resolveDependencies(artifact);
        assertEquals(1, closure.size());
        assertEquals("fabricloader", closure.get(0).name());
    }

    @Test
    void modrinthSynthesisesDeterministicallyWithoutSnapshot() {
        ModrinthClient client = new ModrinthClient(UNREACHABLE, null, false);
        Artifact first = client.resolve(ArtifactSpec.modrinth("unknown-mod", "9.9.9", ArtifactRole.MOD));
        Artifact second = client.resolve(ArtifactSpec.modrinth("unknown-mod", "9.9.9", ArtifactRole.MOD));
        assertEquals(first, second);
        assertEquals("modrinth:unknown-mod", first.id());
        assertEquals("true", first.metadata().get("synthesised"));
    }

    @Test
    void curseForgePaginatesFiftyPerPageWithTenThousandCap() {
        CurseForgeClient client = new CurseForgeClient(UNREACHABLE, null, false);
        assertEquals(0, client.pageCount(0));
        assertEquals(1, client.pageCount(50));
        assertEquals(2, client.pageCount(51));
        assertEquals(200, client.pageCount(CurseForgeClient.MAX_RESULTS));
        assertEquals(200, client.pageCount(50_000), "超过 10000 条上限后不再增长");
        assertEquals(199, client.lastPageIndex());
    }

    @Test
    void curseForgeFallsBackToOfflineSnapshot() {
        CurseForgeClient client = new CurseForgeClient(UNREACHABLE, null, false);
        Artifact artifact = client.resolve(ArtifactSpec.curseforge("238222", "15.2.0.27", ArtifactRole.MOD));
        assertEquals("Just Enough Items", artifact.name());
        assertEquals(ArtifactSource.CURSEFORGE, artifact.source());
    }

    @Test
    void packwizResolvesServerSideClosure() {
        PackwizResolver resolver = new PackwizResolver();
        List<Artifact> closure = resolver.closure(PACK_TOML, documents());
        assertEquals(1, closure.size(), "默认只取服务端非可选产物");
        Artifact artifact = closure.get(0);
        assertEquals("P7dR8mSH", artifact.id());
        assertEquals("Fabric API", artifact.name());
        assertEquals("0.100.0+1.21.11", artifact.version());
        assertEquals("both", artifact.metadata().get("side"));
        assertEquals(ArtifactSource.PACKWIZ, artifact.source());
    }

    @Test
    void packwizHonoursSideAndOptionalFlags() {
        PackwizResolver resolver = new PackwizResolver(true, true);
        List<Artifact> closure = resolver.closure(PACK_TOML, documents());
        assertEquals(3, closure.size());
        assertTrue(closure.stream().anyMatch(artifact -> artifact.name().equals("Sodium")));
        assertTrue(closure.stream().anyMatch(artifact -> artifact.name().equals("Iris")));
        assertEquals(3, resolver.fileList(INDEX_TOML).size());
    }

    @Test
    void packwizMetaCarriesHashAndFilename() {
        PackwizResolver resolver = new PackwizResolver();
        Artifact artifact = resolver.parseMeta("fabric-api.pw.toml", FABRIC_API_PW_TOML, ArtifactRole.MOD);
        assertEquals("fabric-api-0.100.0.jar", artifact.metadata().get("filename"));
        assertEquals("sha512", artifact.metadata().get("hash-format"));
        assertEquals("aaaa", artifact.metadata().get("hash"));
        assertEquals("https://example.invalid/fabric-api.jar", artifact.location());
    }

    @Test
    void factoryDispatchesBySource() {
        assertInstanceOf(MavenArtifactResolver.class, ArtifactResolverFactory.forSource(ArtifactSource.MAVEN));
        assertInstanceOf(ModrinthClient.class, ArtifactResolverFactory.forSource(ArtifactSource.MODRINTH));
        assertInstanceOf(CurseForgeClient.class, ArtifactResolverFactory.forSource(ArtifactSource.CURSEFORGE));
        assertInstanceOf(PackwizResolver.class, ArtifactResolverFactory.forSource(ArtifactSource.PACKWIZ));

        ArtifactResolver resolver = ArtifactResolverFactory.defaultResolver();
        Artifact artifact = resolver.resolve(ArtifactSpec.maven("io.laplace.sprite:probe:2.0.0",
                ArtifactRole.PLUGIN));
        assertEquals("2.0.0", artifact.version());
        assertFalse(resolver.resolveDependencies(artifact).stream().findAny().isPresent());
    }

    private static Map<String, String> documents() {
        return Map.of(
                "index.toml", INDEX_TOML,
                "mods/fabric-api.pw.toml", FABRIC_API_PW_TOML,
                "mods/sodium.pw.toml", SODIUM_PW_TOML,
                "mods/iris.pw.toml", IRIS_PW_TOML);
    }
}
