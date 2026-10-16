package io.laplace.sprite.report;

import io.laplace.sprite.core.io.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 证据链校验（4.6）：内容改动、链接删除 / 重排、mac 伪造三类都要查得出来。 */
class EvidenceChainVerifierTest {

    private static final byte[] KEY = "test-hmac-key-0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path dir;

    @Test
    void validChainPasses() {
        EvidenceVault vault = new EvidenceVault(dir, KEY);
        vault.archive("run-1", "log", "one", "1");
        vault.archive("run-1", "log", "two", "2");

        EvidenceChainVerifier.ChainVerification verification = new EvidenceChainVerifier(vault).verify();
        assertTrue(verification.valid());
        assertEquals(2, verification.checkedLinks());
        assertEquals(-1, verification.firstBrokenIndex());
    }

    @Test
    void modifiedPayloadIsDetected() throws IOException {
        EvidenceVault vault = new EvidenceVault(dir, KEY);
        String path = vault.archive("run-1", "log", "original", "1").path();
        // 只改落盘内容，不动链：payloadHash 必然对不上
        Files.writeString(Path.of(path), "tampered", StandardCharsets.UTF_8);

        EvidenceChainVerifier.ChainVerification verification = new EvidenceChainVerifier(vault).verify();
        assertFalse(verification.valid());
        assertEquals(0, verification.firstBrokenIndex());
        assertTrue(verification.reason().contains("payloadHash"), verification.reason());
    }

    @Test
    void removedLinkIsDetected() {
        EvidenceVault vault = new EvidenceVault(dir, KEY);
        vault.archive("run-1", "log", "one", "1");
        vault.archive("run-1", "log", "two", "2");
        vault.archive("run-1", "log", "three", "3");

        List<EvidenceChainLink> links = new ArrayList<>(vault.chain());
        links.remove(1);
        writeChain(links);

        EvidenceChainVerifier.ChainVerification verification = verifierAfterReload().verify();
        assertFalse(verification.valid());
        assertTrue(verification.reason().contains("链长"), verification.reason());
    }

    @Test
    void reorderedLinkIsDetected() {
        EvidenceVault vault = new EvidenceVault(dir, KEY);
        vault.archive("run-1", "log", "one", "1");
        vault.archive("run-1", "log", "two", "2");

        List<EvidenceChainLink> links = new ArrayList<>(vault.chain());
        List<EvidenceChainLink> swapped = List.of(links.get(1), links.get(0));
        assertNotEquals(swapped, links);
        writeChain(swapped);

        EvidenceChainVerifier.ChainVerification verification = verifierAfterReload().verify();
        assertFalse(verification.valid());
        assertEquals(0, verification.firstBrokenIndex());
        assertTrue(verification.reason().contains("index"), verification.reason());
    }

    @Test
    void splicedPrevMacIsDetected() {
        EvidenceVault vault = new EvidenceVault(dir, KEY);
        vault.archive("run-1", "log", "one", "1");
        vault.archive("run-1", "log", "two", "2");

        List<EvidenceChainLink> links = new ArrayList<>(vault.chain());
        EvidenceChainLink second = links.get(1);
        links.set(1, new EvidenceChainLink(second.index(), second.timestamp(), second.payloadHash(),
                EvidenceChainLink.GENESIS_PREV_MAC, second.mac(), second.evidenceId()));
        writeChain(links);

        EvidenceChainVerifier.ChainVerification verification = verifierAfterReload().verify();
        assertFalse(verification.valid());
        assertEquals(1, verification.firstBrokenIndex());
        assertTrue(verification.reason().contains("prevMac"), verification.reason());
    }

    @Test
    void forgedMacIsDetected() {
        EvidenceVault vault = new EvidenceVault(dir, KEY);
        vault.archive("run-1", "log", "one", "1");
        vault.archive("run-1", "log", "two", "2");

        List<EvidenceChainLink> links = new ArrayList<>(vault.chain());
        EvidenceChainLink second = links.get(1);
        String forged = "f".repeat(64);
        links.set(1, new EvidenceChainLink(second.index(), second.timestamp(), second.payloadHash(),
                second.prevMac(), forged, second.evidenceId()));
        writeChain(links);

        EvidenceChainVerifier.ChainVerification verification = verifierAfterReload().verify();
        assertFalse(verification.valid());
        assertEquals(1, verification.firstBrokenIndex());
        assertTrue(verification.reason().contains("mac"), verification.reason());
    }

    private void writeChain(List<EvidenceChainLink> links) {
        Json.writeFile(dir.resolve("chain.json"), links);
    }

    private EvidenceChainVerifier verifierAfterReload() {
        return new EvidenceChainVerifier(new EvidenceVault(dir, KEY));
    }
}
