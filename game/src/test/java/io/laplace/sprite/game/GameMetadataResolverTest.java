package io.laplace.sprite.game;

import io.laplace.sprite.datapack.PackFormatTable;
import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 版本 JSON 到元数据的解析规则。 */
class GameMetadataResolverTest {

    private final GameMetadataResolver resolver = new GameMetadataResolver();
    private final PackFormatTable packFormats = new PackFormatTable();

    @Test
    void snapshotsAt264UseVulkan() {
        GameVersionMetadata metadata = resolver.resolve("26.4-snapshot-1",
                versionJson("26.4-snapshot-1", "snapshot"));

        assertEquals(RenderApi.VULKAN, metadata.defaultRenderApi());
        assertEquals(packFormats.formatFor("26.4-snapshot-1"), metadata.packFormat());
        assertEquals(MappingSet.MOJMAP, metadata.mappings());
        assertTrue(metadata.dataVersion().isEmpty(), "26.4 无正式版，不猜 dataVersion");
        assertTrue(metadata.protocolVersion().isEmpty(), "26.4 无正式版，不猜 protocolVersion");
    }

    @Test
    void release263UsesKnownNumbersAndOpenGl() {
        GameVersionMetadata metadata = resolver.resolve("26.3", versionJson("26.3", "release"));

        assertEquals(RenderApi.OPENGL, metadata.defaultRenderApi());
        assertEquals(87, metadata.packFormat().orElseThrow());
        assertEquals(OptionalInt.of(5023), metadata.dataVersion());
        assertEquals(OptionalInt.of(777), metadata.protocolVersion());
        assertEquals(MappingSet.MOJMAP, metadata.mappings());
        assertEquals("net.minecraft.client.main.Main", metadata.mainClass());
        assertEquals("34", metadata.assets());
        assertEquals(10, metadata.client().size());
        assertEquals("https://example.invalid/client.jar", metadata.client().url());
        assertNull(metadata.client().sha256(), "官方版本 JSON 通常不给 sha256");
    }

    @Test
    void unknownVersionsLeaveNumbersEmpty() {
        GameVersionMetadata metadata = resolver.resolve("1.20.6", versionJson("1.20.6", "release"));

        assertEquals(packFormats.formatFor("1.20.6"), metadata.packFormat());
        assertTrue(metadata.dataVersion().isEmpty());
        assertTrue(metadata.protocolVersion().isEmpty());
        assertEquals(MappingSet.YARN, metadata.mappings());
    }

    @Test
    void mappingsFollowLoaderRules() {
        assertEquals(MappingSet.YARN, GameMetadataResolver.mappingsFor("1.21.11", null));
        assertEquals(MappingSet.YARN, GameMetadataResolver.mappingsFor("1.21.11", "fabric"));
        assertEquals(MappingSet.MOJMAP, GameMetadataResolver.mappingsFor("26.1", "fabric"));
        assertEquals(MappingSet.MOJMAP, GameMetadataResolver.mappingsFor("1.21.11", "neoforge"));
        assertEquals(MappingSet.MOJMAP, GameMetadataResolver.mappingsFor("1.21.11", "Forge"));
        assertEquals(MappingSet.OBFUSCATED,
                GameMetadataResolver.mappingsFor("1.21.12", "fabric"));
    }

    @Test
    void rejectsMalformedVersionJson() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve("26.3", "{"));
        assertTrue(ex.getMessage().contains("26.3"), ex.getMessage());
    }

    private static String versionJson(String id, String type) {
        return """
                {
                  "id": "%s",
                  "type": "%s",
                  "releaseTime": "2026-09-15T12:00:00+00:00",
                  "mainClass": "net.minecraft.client.main.Main",
                  "assets": "34",
                  "assetIndex": {
                    "id": "34",
                    "sha1": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "size": 100,
                    "url": "https://example.invalid/assets.json"
                  },
                  "downloads": {
                    "client": {
                      "url": "https://example.invalid/client.jar",
                      "sha1": "0123456789abcdef0123456789abcdef01234567",
                      "size": 10
                    },
                    "server": {
                      "url": "https://example.invalid/server.jar",
                      "sha1": "fedcba9876543210fedcba9876543210fedcba98",
                      "size": 20
                    }
                  }
                }
                """.formatted(id, type);
    }
}
