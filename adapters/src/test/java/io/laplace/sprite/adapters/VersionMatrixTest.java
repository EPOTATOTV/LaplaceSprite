package io.laplace.sprite.adapters;

import io.laplace.sprite.adapters.matrix.FabricMetaSource;
import io.laplace.sprite.adapters.matrix.ForgePromotionsSource;
import io.laplace.sprite.adapters.matrix.NeoForgeProjectsSource;
import io.laplace.sprite.adapters.matrix.VersionMatrixRegistry;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.model.VersionMatrixEntry;
import io.laplace.sprite.core.spi.VersionOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 版本矩阵（14.7）：运行时拉取 + 本地快照 + 手动 pin。 */
class VersionMatrixTest {

    private static final URI UNREACHABLE = URI.create("http://127.0.0.1:1/version-matrix");

    @Test
    void versionOrderComparesDottedSegments() {
        assertTrue(VersionOrder.compare("1.21.11", "1.21.9") > 0);
        assertTrue(VersionOrder.compare("0.19.5", "0.14.0") > 0);
        assertEquals(0, VersionOrder.compare("0.19.5", "0.19.5"));
        assertTrue(VersionOrder.compare("1.21.11", "1.21.11") == 0);
        assertTrue(VersionOrder.compare("21.11.0", "20.2") > 0);
    }

    @Test
    void registryAcceptsInMatrixKeysAndRejectsOutOfRange() {
        VersionMatrixRegistry registry = VersionMatrixRegistry.defaults();

        assertTrue(registry.supports(new PlatformKey(PlatformFamily.FABRIC, "1.21.11", "0.19.5")));
        assertTrue(registry.supports(new PlatformKey(PlatformFamily.QUILT, "1.21.1", "0.26.0")));
        assertTrue(registry.supports(new PlatformKey(PlatformFamily.FORGE, "1.21.11", "61.2.3")));
        assertTrue(registry.supports(new PlatformKey(PlatformFamily.NEOFORGE, "1.21.11", "21.11.0")));

        assertFalse(registry.supports(new PlatformKey(PlatformFamily.FABRIC, "1.21.11", "0.99.0")));
        assertFalse(registry.supports(new PlatformKey(PlatformFamily.FABRIC, "1.22.0", "0.19.5")));
        assertFalse(registry.supports(PlatformKey.of(PlatformFamily.PAPER, "1.21.11")));
    }

    @Test
    void snapshotsAreBundledOffline() {
        VersionMatrixRegistry registry = VersionMatrixRegistry.defaults();
        assertTrue(registry.snapshot(PlatformFamily.FABRIC).size() >= 5);
        assertTrue(registry.snapshot(PlatformFamily.QUILT).size() >= 3);
        assertTrue(registry.snapshot(PlatformFamily.FORGE).size() >= 3);
        assertTrue(registry.snapshot(PlatformFamily.NEOFORGE).size() >= 3);
        assertEquals("fabric-meta", registry.snapshot(PlatformFamily.FABRIC).get(0).source());
    }

    @Test
    void fetchFallsBackToSnapshotWhenEndpointIsUnreachable() {
        FabricMetaSource source = new FabricMetaSource(UNREACHABLE, true, null);
        List<VersionMatrixEntry> entries = source.fetch(PlatformFamily.FABRIC);
        assertFalse(entries.isEmpty(), "端点不可达时必须退化到本地快照");
        assertTrue(entries.stream().anyMatch(entry -> "0.19.5".equals(entry.loaderVersion())));
        assertTrue(source.fetch(PlatformFamily.FABRIC).stream()
                .allMatch(entry -> entry.source().equals("fabric-meta")));
        assertTrue(source.fetch(PlatformFamily.QUILT).isEmpty());
    }

    @Test
    void otherSourcesFallBackToo() {
        assertFalse(new ForgePromotionsSource(UNREACHABLE, true, null).fetch(PlatformFamily.FORGE).isEmpty());
        assertFalse(new NeoForgeProjectsSource(UNREACHABLE, true, null).fetch(PlatformFamily.NEOFORGE).isEmpty());
    }

    @Test
    void neoforgeLoaderVersionMapsBackToMinecraft() {
        assertEquals("1.21.11", NeoForgeProjectsSource.minecraftOf("21.11.0"));
        assertEquals("1.21.1", NeoForgeProjectsSource.minecraftOf("21.1.209"));
        assertNull(NeoForgeProjectsSource.minecraftOf("21"));
    }

    @Test
    void pinIsPersistedAndReloadable(@TempDir Path tempDir) {
        Path pinFile = tempDir.resolve("pins").resolve("matrix-pin.json");
        FabricMetaSource source = new FabricMetaSource(false, pinFile);
        VersionMatrixEntry pinned = new VersionMatrixEntry(PlatformFamily.FABRIC, "1.21.11", "0.19.9",
                "manual", Instant.parse("2026-02-01T00:00:00Z"), true);
        source.pin(pinned);
        assertTrue(source.pinFile().toFile().isFile(), "pin 必须落盘");

        FabricMetaSource reopened = new FabricMetaSource(false, pinFile);
        assertEquals(1, reopened.pinned().size());
        assertEquals("0.19.9", reopened.pinned().get(0).loaderVersion());
        assertTrue(reopened.pinned().get(0).pinned());
    }

    @Test
    void registryPinsOverrideSnapshot() {
        VersionMatrixRegistry registry = VersionMatrixRegistry.defaults();
        registry.pin(new VersionMatrixEntry(PlatformFamily.QUILT, "1.21.11", "0.88.0", "manual",
                Instant.parse("2026-02-01T00:00:00Z"), true));
        assertTrue(registry.supports(new PlatformKey(PlatformFamily.QUILT, "1.21.11", "0.88.0")));
        assertEquals(1, registry.pinned(PlatformFamily.QUILT).size());
    }

    @Test
    void unknownFamilyHasNoMatrixEntries() {
        VersionMatrixRegistry registry = VersionMatrixRegistry.defaults();
        assertTrue(registry.snapshot(PlatformFamily.BUKKIT).isEmpty());
        assertFalse(registry.supports(PlatformFamily.BUKKIT));
        assertTrue(registry.sourceFor(PlatformFamily.BUKKIT).isEmpty());
    }
}
