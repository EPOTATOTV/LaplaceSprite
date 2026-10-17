package io.laplace.sprite.report;

import io.laplace.sprite.core.model.EvidenceRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 证据库远端同步（4.6）：本地镜像走通上传 / 校验，链在同步后仍自洽，未配置时不假装上传。 */
class EvidenceRemoteSyncTest {

    private static final byte[] KEY = "test-hmac-key-0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path vaultDir;

    @TempDir
    Path mirrorDir;

    @Test
    void syncUploadsAllArchivedPayloadsAndKeepsChainValid() {
        EvidenceVault vault = new EvidenceVault(vaultDir, KEY);
        EvidenceRecord first = vault.archive("run-1", "log", "one", "误报");
        vault.archive("run-1", "flag", "two", "检出");

        LocalMirrorRemoteStore store = new LocalMirrorRemoteStore(mirrorDir);
        RemoteSyncReport report = vault.syncRemote(store);

        assertEquals(2, report.attempted());
        assertEquals(2, report.uploaded());
        assertEquals(0, report.skipped());
        assertEquals(0, report.failed());
        assertTrue(report.failures().isEmpty());
        assertEquals(2, vault.remoteCount());
        assertEquals("run-1/log-" + first.id() + ".txt", vault.remoteRef(first.id()).orElseThrow());
        assertTrue(store.exists(vault.remoteRef(first.id()).orElseThrow()));
        assertEquals("one", new String(store.get(vault.remoteRef(first.id()).orElseThrow()),
                StandardCharsets.UTF_8));

        RemoteVerification verification = vault.verifyRemote(store);
        assertTrue(verification.consistent(), verification.failures().toString());
        assertEquals(2, verification.checked());
        assertEquals(2, verification.matched());

        // 远端同步只写 remote-index.json，链与索引不动，校验照旧通过
        assertTrue(new EvidenceChainVerifier(vault).verify().valid());
        assertTrue(report.toMarkdown().contains("新上传"));
    }

    @Test
    void syncIsIdempotentAndSurvivesRestart() {
        EvidenceVault vault = new EvidenceVault(vaultDir, KEY);
        vault.archive("run-1", "log", "one", "1");
        LocalMirrorRemoteStore store = new LocalMirrorRemoteStore(mirrorDir);
        vault.syncRemote(store);

        RemoteSyncReport second = vault.syncRemote(store);
        assertEquals(0, second.uploaded());
        assertEquals(1, second.skipped());

        EvidenceVault reopened = new EvidenceVault(vaultDir, KEY);
        assertEquals(1, reopened.remoteCount(), "远端引用索引应在重启后恢复");
        assertTrue(reopened.verifyRemote(store).consistent());
    }

    @Test
    void tamperedRemoteCopyIsReportedAsFailure() throws IOException {
        EvidenceVault vault = new EvidenceVault(vaultDir, KEY);
        EvidenceRecord record = vault.archive("run-1", "log", "original", "1");
        LocalMirrorRemoteStore store = new LocalMirrorRemoteStore(mirrorDir);
        vault.syncRemote(store);

        // 只改远端副本，链与本地文件不动
        Files.writeString(mirrorDir.resolve(vault.remoteRef(record.id()).orElseThrow()), "swapped",
                StandardCharsets.UTF_8);

        RemoteVerification verification = vault.verifyRemote(store);
        assertFalse(verification.consistent());
        assertEquals(1, verification.mismatched());
        assertTrue(verification.failures().get(0).contains("payloadHash"), verification.failures().toString());
        assertTrue(new EvidenceChainVerifier(vault).verify().valid(), "本地链不受远端改动影响");
    }

    @Test
    void unconfiguredStoreReportsNoUploadsInsteadOfPretending() {
        EvidenceVault vault = new EvidenceVault(vaultDir, KEY);
        vault.archive("run-1", "log", "one", "1");
        HttpRemoteStore unconfigured = new HttpRemoteStore(Map.of(), request -> {
            throw new AssertionError("未配置的存储不该发出请求");
        });

        RemoteSyncReport report = vault.syncRemote(unconfigured);
        assertEquals(0, report.uploaded());
        assertEquals(1, report.failed());
        assertEquals(0, vault.remoteCount());
        assertEquals(1, report.failures().size());
        assertTrue(report.failures().get(0).contains("未配置"), report.failures().toString());

        RemoteVerification verification = vault.verifyRemote(unconfigured);
        assertFalse(verification.consistent());
        assertEquals(0, verification.checked());
        assertTrue(verification.failures().get(0).contains("无法校验远端副本"));
    }

    @Test
    void missingRemoteObjectIsReported() {
        EvidenceVault vault = new EvidenceVault(vaultDir, KEY);
        EvidenceRecord record = vault.archive("run-1", "log", "one", "1");
        LocalMirrorRemoteStore store = new LocalMirrorRemoteStore(mirrorDir);
        vault.syncRemote(store);

        Path mirrored = mirrorDir.resolve(vault.remoteRef(record.id()).orElseThrow());
        assertTrue(mirrored.toFile().delete(), "删除镜像副本以模拟远端丢对象");

        RemoteVerification verification = vault.verifyRemote(store);
        assertFalse(verification.consistent());
        assertEquals(1, verification.missing());
        assertEquals(1, verification.failures().size());
        assertTrue(verification.failures().get(0).contains("缺失"));
    }
}
