package io.laplace.sprite.replay;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldSnapshotTagTest {

    @Test
    void contentHashIsStableUnderManifestReordering() {
        Map<String, String> first = new LinkedHashMap<>();
        first.put("fabric-api", "0.92.0");
        first.put("carpet", "1.4.57");
        Map<String, String> reordered = new LinkedHashMap<>();
        reordered.put("carpet", "1.4.57");
        reordered.put("fabric-api", "0.92.0");

        assertEquals(WorldSnapshotTag.contentHash(first), WorldSnapshotTag.contentHash(reordered));
        assertEquals(WorldSnapshotTag.contentHash(List.of("b 2", "a 1")),
                WorldSnapshotTag.contentHash(List.of("a 1", "b 2")));
    }

    @Test
    void labelIsDeterministicAndFilesystemSafe() {
        WorldSnapshotTag first = WorldSnapshotTag.of("seed 42", "1.20.1",
                Map.of("carpet", "1.4.57"), "scene/crash:42");
        WorldSnapshotTag second = WorldSnapshotTag.of("seed 42", "1.20.1",
                Map.of("carpet", "1.4.57"), "scene/crash:42");

        assertEquals(first, second);
        assertEquals(first.label(), second.label());
        assertTrue(first.label().matches("[A-Za-z0-9._-]+"));
        assertTrue(first.label().startsWith("snap-"));
        assertTrue(first.label().contains("seed_42"));
        assertTrue(first.label().contains("scene_crash_42"));

        WorldSnapshotTag otherScene = WorldSnapshotTag.of("seed 42", "1.20.1",
                Map.of("carpet", "1.4.57"), "scene-other");
        assertNotEquals(first.label(), otherScene.label());
    }
}
