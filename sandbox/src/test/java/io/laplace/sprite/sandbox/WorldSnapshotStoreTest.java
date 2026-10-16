package io.laplace.sprite.sandbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 世界快照（15.4）：基线建立、回灌清残留、全新 superflat。 */
class WorldSnapshotStoreTest {

    @Test
    void restoreBringsBackBaselineAndDropsScenarioResidue(@TempDir Path tempDir) throws IOException {
        Path data = tempDir.resolve("data");
        Files.createDirectories(data.resolve("world/region"));
        Files.writeString(data.resolve("level.dat"), "baseline");
        Files.writeString(data.resolve("world/region/r.0.0.mca"), "region");

        WorldSnapshotStore store = new WorldSnapshotStore(tempDir.resolve("snapshots"));
        assertFalse(store.hasBaseline("case-1"));
        assertFalse(store.restoreBaseline("case-1", data), "没有基线时不做恢复");

        Path baseline = store.snapshotBaseline("case-1", data);
        assertTrue(store.hasBaseline("case-1"));
        assertTrue(Files.isDirectory(baseline));

        Files.writeString(data.resolve("level.dat"), "dirty");
        Files.writeString(data.resolve("pollution.tmp"), "junk");
        Files.delete(data.resolve("world/region/r.0.0.mca"));

        assertTrue(store.restoreBaseline("case-1", data));
        assertEquals("baseline", Files.readString(data.resolve("level.dat")), "内容回到基线");
        assertTrue(Files.exists(data.resolve("world/region/r.0.0.mca")), "被删的文件要回来");
        assertFalse(Files.exists(data.resolve("pollution.tmp")), "场景残留必须清掉");
    }

    @Test
    void snapshotOfMissingDataDirYieldsEmptyBaseline(@TempDir Path tempDir) {
        WorldSnapshotStore store = new WorldSnapshotStore(tempDir.resolve("snapshots"));
        store.snapshotBaseline("case-2", tempDir.resolve("not-created"));
        assertTrue(store.hasBaseline("case-2"));
        assertTrue(store.restoreBaseline("case-2", tempDir.resolve("not-created")));
    }

    @Test
    void freshSuperflatResetsData(@TempDir Path tempDir) throws IOException {
        Path data = tempDir.resolve("data");
        Files.createDirectories(data);
        Files.writeString(data.resolve("junk.txt"), "junk");

        WorldSnapshotStore store = new WorldSnapshotStore(tempDir.resolve("snapshots"));
        Path fresh = store.freshSuperflat("case-3", data);

        assertEquals(data, fresh);
        assertFalse(Files.exists(data.resolve("junk.txt")));
        String properties = Files.readString(data.resolve("server.properties"));
        assertTrue(properties.contains("level-type=minecraft:flat"));
        assertTrue(properties.contains("level-name=world"));
    }

    @Test
    void invalidInputsAreRejected(@TempDir Path tempDir) {
        assertThrows(IllegalArgumentException.class, () -> new WorldSnapshotStore(null));
        WorldSnapshotStore store = new WorldSnapshotStore(tempDir);
        assertThrows(IllegalArgumentException.class, () -> store.baselineDirectory(" "));
        assertThrows(IllegalArgumentException.class, () -> store.freshSuperflat("", tempDir.resolve("data")));
    }
}
