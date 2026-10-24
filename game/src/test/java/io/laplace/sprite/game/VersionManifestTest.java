package io.laplace.sprite.game;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 官方版本清单解析。 */
class VersionManifestTest {

    private final VersionManifest manifest = loadFixture();

    @Test
    void parsesAtLeastFiftyFiveVersions() {
        assertTrue(manifest.count() >= 55, "fixture 至少 55 个版本");
        assertEquals("26.3", manifest.latestRelease());
        assertEquals("26.4-snapshot-5", manifest.latestSnapshot());
    }

    @Test
    void findsKnownVersionAndReturnsEmptyForUnknown() {
        GameVersion version = manifest.find("26.3").orElseThrow();
        assertEquals("release", version.type());
        assertEquals("0000000000000000000000000000000000000006", version.sha1());
        assertTrue(manifest.find("9.99.99").isEmpty());
        assertTrue(manifest.find(null).isEmpty());
    }

    @Test
    void groupsVersionsByType() {
        List<GameVersion> releases = manifest.byType("release");
        List<GameVersion> snapshots = manifest.byType("snapshot");
        List<GameVersion> betas = manifest.byType("old_beta");
        assertFalse(releases.isEmpty());
        assertFalse(snapshots.isEmpty());
        assertFalse(betas.isEmpty());
        assertTrue(manifest.byType("unknown").isEmpty());
    }

    @Test
    void rejectsBrokenJsonWithContext() {
        IllegalArgumentException malformed = assertThrows(IllegalArgumentException.class,
                () -> VersionManifest.parse("{\"latest\": {"));
        assertTrue(malformed.getMessage().contains("解析失败"), malformed.getMessage());

        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> VersionManifest.parse("{\"latest\":{\"release\":\"26.3\","
                        + "\"snapshot\":\"26.4-snapshot-5\"}}"));
        assertTrue(missing.getMessage().contains("versions"), missing.getMessage());
    }

    private static VersionManifest loadFixture() {
        try (InputStream in = VersionManifestTest.class
                .getResourceAsStream("/version_manifest_v2.json")) {
            assertNotNull(in, "缺少 version_manifest_v2.json fixture");
            return VersionManifest.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("读取 fixture 失败", ex);
        }
    }
}
