package io.laplace.sprite.report;

import io.laplace.sprite.core.model.EvidenceRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceVaultTest {

    @TempDir
    Path dir;

    @Test
    void archiveWritesBlobAndSurvivesRestart() {
        EvidenceVault vault = new EvidenceVault(dir);
        EvidenceRecord record = vault.archive("run-1", "log", "logging content", "误报归档");

        assertEquals(1, vault.size());
        assertEquals(1, vault.byRun("run-1").size());
        assertTrue(Files.exists(Path.of(record.path())), "证据 blob 未落盘");

        EvidenceVault reopened = new EvidenceVault(dir);
        assertEquals(1, reopened.size());
        assertTrue(reopened.byId(record.id()).isPresent(), "索引未在重启后恢复");
        assertEquals(1, reopened.byRun("run-1").size());
    }

    @Test
    void idsAreUniqueAcrossRuns() {
        EvidenceVault vault = new EvidenceVault(dir);
        EvidenceRecord first = vault.archive("run-1", "log", "a", "1");
        EvidenceRecord second = vault.archive("run-2", "metrics", "b", "2");

        assertEquals(2, vault.size());
        assertEquals(2, vault.all().size());
        assertNotEquals(first.id(), second.id());
    }
}
