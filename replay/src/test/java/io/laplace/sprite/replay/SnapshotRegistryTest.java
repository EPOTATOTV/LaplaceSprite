package io.laplace.sprite.replay;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotRegistryTest {

    @TempDir
    Path tempDir;

    @Test
    void bindsSceneIdAndSnapshotBidirectionallyAcrossJsonRoundTrip() throws IOException {
        Path indexFile = tempDir.resolve(SnapshotRegistry.DEFAULT_INDEX_FILE);
        WorldSnapshotTag tag = WorldSnapshotTag.of("seed-1", "1.20.1",
                List.of("fabric-api 0.92.0", "carpet 1.4.57"), "scene-crash-42");
        Path snapshot = tempDir.resolve("snapshots").resolve(tag.label());
        Files.createDirectories(snapshot);
        Path mcpr = tempDir.resolve("scenes").resolve("crash-42.mcpr");
        Files.createDirectories(mcpr.getParent());
        Files.writeString(mcpr, "stub");

        SnapshotRegistry registry = new SnapshotRegistry(indexFile);
        registry.bind(tag, snapshot, mcpr);

        assertEquals(Optional.of(tag), registry.findBySceneId("scene-crash-42"));
        assertEquals(Optional.of(tag), registry.tagOf(snapshot));
        assertEquals(Optional.of(snapshot.toAbsolutePath().normalize()),
                registry.snapshotPathOfScene("scene-crash-42"));
        assertEquals(Optional.of(mcpr.toAbsolutePath().normalize()),
                registry.mcprPathOfScene("scene-crash-42"));

        registry.save();
        assertTrue(Files.isRegularFile(indexFile));

        SnapshotRegistry reloaded = SnapshotRegistry.open(indexFile);

        assertEquals(1, reloaded.bindings().size());
        assertEquals(Optional.of(tag), reloaded.findBySceneId("scene-crash-42"));
        assertEquals(Optional.of(tag), reloaded.tagOf(snapshot));
        assertEquals(Optional.of(snapshot.toAbsolutePath().normalize()),
                reloaded.snapshotPathOfScene("scene-crash-42"));
        assertEquals(Optional.of(mcpr.toAbsolutePath().normalize()),
                reloaded.mcprPathOfScene("scene-crash-42"));
        String json = Files.readString(indexFile);
        assertTrue(json.contains("\"bindings\""));
        assertTrue(json.contains("\"sceneId\""));
        assertTrue(json.contains("\"snapshotPath\""));
        assertTrue(json.contains("\"mcprPath\""));
    }

    @Test
    void rebindingSameSceneReplacesOldSnapshot() {
        WorldSnapshotTag first = WorldSnapshotTag.of("seed-1", "1.20.1", List.of("a 1"), "scene-7");
        WorldSnapshotTag second = WorldSnapshotTag.of("seed-1", "1.20.1", List.of("a 1"), "scene-7");
        Path oldSnapshot = tempDir.resolve("snapshots").resolve("old");
        Path newSnapshot = tempDir.resolve("snapshots").resolve("new");

        SnapshotRegistry registry = new SnapshotRegistry();
        registry.bind(first, oldSnapshot, tempDir.resolve("old.mcpr"));
        registry.bind(second, newSnapshot, tempDir.resolve("new.mcpr"));

        assertEquals(1, registry.bindings().size());
        assertEquals(Optional.of(second), registry.findBySceneId("scene-7"));
        assertEquals(Optional.of(second), registry.tagOf(newSnapshot));
        assertTrue(registry.tagOf(oldSnapshot).isEmpty());
    }
}
