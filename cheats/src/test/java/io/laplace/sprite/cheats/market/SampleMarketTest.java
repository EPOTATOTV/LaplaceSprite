package io.laplace.sprite.cheats.market;

import io.laplace.sprite.cheats.CheatCatalog;
import io.laplace.sprite.cheats.SampleCategory;
import io.laplace.sprite.cheats.SampleSpec;
import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.core.model.SampleTier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 样本市场（L-08）：签名校验、安装、版本历史与回滚。 */
class SampleMarketTest {

    private static final byte[] KEY = "laplace-market-test-key".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path tempDir;

    @Test
    void signedPackageInstallsAndBecomesCurrent() {
        SampleMarket market = new SampleMarket(tempDir, KEY);
        SamplePackage pkg = signed("1.0.0");

        SampleMarket.InstallRecord record = market.install(pkg);

        assertEquals("install", record.action());
        assertEquals("community-pack@1.0.0", record.packageId() + "@" + record.version());
        assertEquals(List.of("sample.a"), record.sampleIds());
        assertEquals("1.0.0", market.current("community-pack").orElseThrow().version());
        assertTrue(record.evidencePayload().contains("\"action\" : \"install\""), record.evidencePayload());
    }

    @Test
    void tamperedPackageIsRejectedBeforeAnythingIsWritten() {
        SampleMarket market = new SampleMarket(tempDir, KEY);
        SamplePackage signed = signed("1.0.0");
        SamplePackage tampered = new SamplePackage(signed.id(), signed.version(), "attacker",
                signed.samples(), signed.signature(), signed.trustScore(), signed.sourceRef());

        assertFalse(market.verify(tampered));
        assertThrows(SecurityException.class, () -> market.install(tampered));
        assertTrue(market.current("community-pack").isEmpty());
    }

    @Test
    void rollbackReturnsToThePreviousVersionAndKeepsTheFile() {
        SampleMarket market = new SampleMarket(tempDir, KEY);
        market.install(signed("1.0.0"));
        market.install(signed("1.1.0"));

        SampleMarket.InstallRecord rollback = market.rollback("community-pack");

        assertEquals("rollback", rollback.action());
        assertEquals("1.0.0", rollback.version());
        assertEquals("1.0.0", market.current("community-pack").orElseThrow().version());
        assertEquals(List.of("1.0.0", "1.1.0"), market.history("community-pack"));
    }

    @Test
    void rollbackWithoutHistoryIsRejected() {
        SampleMarket market = new SampleMarket(tempDir, KEY);
        market.install(signed("1.0.0"));

        assertThrows(IllegalStateException.class, () -> market.rollback("community-pack"));
    }

    @Test
    void reinstallingTheSameVersionDoesNotDuplicateHistory() {
        SampleMarket market = new SampleMarket(tempDir, KEY);
        market.install(signed("1.0.0"));
        market.install(signed("1.0.0"));

        assertEquals(List.of("1.0.0"), market.history("community-pack"));
        assertEquals(1, market.all().size());
    }

    @Test
    void packageRoundTripsThroughJsonForTheCli() {
        SamplePackage signed = signed("1.0.0");

        String json = Json.write(signed);
        SamplePackage parsed = Json.read(json, SamplePackage.class);

        assertEquals(signed.id(), parsed.id());
        assertEquals(signed.signature(), parsed.signature());
        assertEquals(signed.sampleIds(), parsed.sampleIds());
        assertTrue(SamplePackageSigner.verify(parsed, KEY));
    }

    @Test
    void installedSamplesMergeIntoTheCatalog() {
        SampleMarket market = new SampleMarket(tempDir, KEY);
        market.install(signed("1.0.0"));

        CheatCatalog catalog = new CheatCatalog(market.installedSamples());

        assertTrue(catalog.sampleById("sample.a").isPresent());
        assertTrue(catalog.byCategory(SampleCategory.DATAPACK).stream()
                .anyMatch(sample -> sample.id().equals("sample.a")));
        assertTrue(catalog.totalSpecs() > new CheatCatalog().totalSpecs());
    }

    @Test
    void trustScoreIsCoveredByTheSignature() {
        SamplePackage signed = signed("1.0.0");
        SamplePackage inflated = new SamplePackage(signed.id(), signed.version(), signed.publisher(),
                signed.samples(), signed.signature(), 1.0, signed.sourceRef());

        assertFalse(new SampleMarket(tempDir, KEY).verify(inflated), "可信度被改动后签名必须失效");
    }

    @Test
    void revokedVersionCannotBeInstalledOrRolledBackTo() {
        SampleMarket market = new SampleMarket(tempDir, KEY);
        market.install(signed("1.0.0"));
        market.install(signed("1.1.0"));

        market.revoke("community-pack", "1.0.0", "来源不可信");

        assertTrue(market.isRevoked("community-pack", "1.0.0"));
        assertThrows(IllegalStateException.class, () -> market.rollback("community-pack"));
        assertThrows(SecurityException.class, () -> market.install(signed("1.0.0")));
    }

    @Test
    void searchRanksByRelevanceThenTrust() {
        SampleMarket market = new SampleMarket(tempDir, KEY);
        market.install(signed("1.0.0"));
        SamplePackage other = SamplePackageSigner.sign(new SamplePackage("other-pack", "2.0.0",
                "laplace-community", List.of(sample("community.sample")), "", 0.4,
                "https://example.invalid/other"), KEY);
        market.install(other);

        List<SampleMarket.SearchHit> hits = market.search("community");

        assertEquals("community-pack", hits.get(0).pkg().id(), "精确匹配的 id 相关度更高");
        assertEquals(0.9, hits.get(0).trustScore());
        assertFalse(hits.get(0).revoked());
    }

    @Test
    void sandboxRejectsDuplicateOrMalformedSamplesBeforeWriting() {
        SampleMarket market = new SampleMarket(tempDir, KEY);
        SampleSpec duplicated = sample("Bad Id");
        SamplePackage invalid = SamplePackageSigner.sign(new SamplePackage("bad-pack", "1.0.0",
                "laplace-community", List.of(duplicated, duplicated), "", 0.5, ""), KEY);

        assertThrows(IllegalArgumentException.class, () -> market.install(invalid));
        assertTrue(market.current("bad-pack").isEmpty());
        assertFalse(java.nio.file.Files.exists(tempDir.resolve("bad-pack")));
    }

    private static SampleSpec sample(String id) {
        return new SampleSpec(id, "样本 A", SampleCategory.DATAPACK,
                "PACK_FORMAT_MISMATCH", null, "datapack-runtime", List.<SampleTier>of(),
                "baseline:walk", "datapack", "社区", "");
    }

    private static SamplePackage signed(String version) {
        return SamplePackageSigner.sign(new SamplePackage("community-pack", version, "laplace-community",
                List.of(sample("sample.a")), "", 0.9, "https://example.invalid/community-pack"), KEY);
    }
}
