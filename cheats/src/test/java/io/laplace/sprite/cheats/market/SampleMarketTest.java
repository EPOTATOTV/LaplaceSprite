package io.laplace.sprite.cheats.market;

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
                signed.samples(), signed.signature());

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

    private static SamplePackage signed(String version) {
        SampleSpec sample = new SampleSpec("sample.a", "样本 A", SampleCategory.DATAPACK,
                "PACK_FORMAT_MISMATCH", null, "datapack-runtime", List.<SampleTier>of(),
                "baseline:walk", "datapack", "社区", "");
        return SamplePackageSigner.sign(new SamplePackage("community-pack", version, "laplace-community",
                List.of(sample), ""), KEY);
    }
}
