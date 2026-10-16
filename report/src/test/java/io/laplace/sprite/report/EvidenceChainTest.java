package io.laplace.sprite.report;

import io.laplace.sprite.core.model.EvidenceRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 证据链（4.6）：链式哈希、重启保留与密钥来源。 */
class EvidenceChainTest {

    private static final byte[] KEY = "test-hmac-key-0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path dir;

    @Test
    void firstLinkStartsFromGenesisAndChainsForward() {
        EvidenceVault vault = new EvidenceVault(dir, KEY);
        EvidenceRecord first = vault.archive("run-1", "log", "a", "1");
        EvidenceRecord second = vault.archive("run-1", "flag", "b", "2");

        List<EvidenceChainLink> chain = vault.chain();
        assertEquals(2, chain.size());
        assertEquals(0, chain.get(0).index());
        assertEquals(EvidenceChainLink.GENESIS_PREV_MAC, chain.get(0).prevMac(), "首环前驱应为创世值");
        assertEquals(64, chain.get(0).prevMac().length(), "创世值为 64 个 0");
        assertEquals(chain.get(0).mac(), chain.get(1).prevMac(), "后一环必须引用前一环的 mac");
        assertEquals(1, chain.get(1).index());
        assertEquals(first.id(), chain.get(0).evidenceId());
        assertEquals(second.id(), chain.get(1).evidenceId());
        assertEquals(EvidenceChainLink.payloadHash("a".getBytes(StandardCharsets.UTF_8)),
                chain.get(0).payloadHash());
        assertEquals(first.createdAt(), chain.get(0).timestamp());
        assertTrue(new EvidenceChainVerifier(vault).verify().valid());
    }

    @Test
    void chainSurvivesRestart() {
        EvidenceVault vault = new EvidenceVault(dir, KEY);
        vault.archive("run-1", "log", "payload", "归档");

        EvidenceVault reopened = new EvidenceVault(dir, KEY);
        assertEquals(1, reopened.size());
        assertEquals(1, reopened.chain().size());
        assertTrue(new EvidenceChainVerifier(reopened).verify().valid(), "重启后链应仍可校验");
    }

    @Test
    void defaultKeyIsGeneratedInVaultAndReused() {
        EvidenceVault vault = new EvidenceVault(dir);
        vault.archive("run-1", "log", "x", "x");
        if (System.getenv(EvidenceVault.HMAC_KEY_ENV) == null) {
            assertTrue(Files.exists(dir.resolve("hmac.key")), "未提供密钥时应在 vault 内生成并复用");
        }

        EvidenceVault reopened = new EvidenceVault(dir);
        assertTrue(new EvidenceChainVerifier(reopened).verify().valid(), "复用同一密钥后链应通过");
    }

    @Test
    void explicitKeyLeavesNoKeyMaterialBehind() {
        EvidenceVault vault = new EvidenceVault(dir, KEY);
        vault.archive("run-1", "log", "x", "x");

        assertFalse(Files.exists(dir.resolve("hmac.key")), "显式密钥不应写出任何密钥文件");
        assertTrue(Files.exists(dir.resolve("chain.json")), "链应随索引入盘");
    }

    @Test
    void chainSummaryRendersJsonAndMarkdown() {
        EvidenceVault vault = new EvidenceVault(dir, KEY);
        vault.archive("run-1", "log", "x", "归档");

        assertTrue(vault.chainJson().contains("payloadHash"));
        String md = vault.chainMarkdown();
        assertTrue(md.contains("| 序号 | 证据 |"));
        assertTrue(md.contains("校验：通过"));
        assertTrue(md.contains("ev-00001"));
    }
}
