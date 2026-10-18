package io.laplace.sprite.report;

import io.laplace.sprite.core.model.FlagEvent;
import io.laplace.sprite.core.model.PlayerType;
import io.laplace.sprite.core.model.RegressionCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 回归台账（L-03）：三类记录持久化、健康度快照与绕过转化。 */
class RegressionLedgerTest {

    private static final Instant NOW = Instant.parse("2026-10-18T00:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void recordsSurviveRestartAndHealthIsWritten() {
        RegressionLedger ledger = new RegressionLedger(tempDir);
        List<RegressionCase> cases = java.util.stream.IntStream.range(0, 10)
                .mapToObj(index -> regression("reg-" + index, "fp-" + index)).toList();
        List<FlagEvent> flags = java.util.stream.IntStream.range(0, 10)
                .mapToObj(index -> falsePositive("fp-" + index)).toList();
        ledger.recordCases(cases);
        ledger.recordFlags(flags);
        ledger.recordBypasses(List.of(
                new RegressionHealth.BypassReport("bypass-1", "SPEED", NOW.minus(Duration.ofDays(1)), true),
                new RegressionHealth.BypassReport("bypass-2", "REACH", NOW.minus(Duration.ofDays(2)), true)));

        RegressionHealth.Health health = ledger.writeHealth();

        assertTrue(health.healthy(), health.violations().toString());
        assertTrue(Files.isRegularFile(tempDir.resolve(RegressionLedger.HEALTH_FILE)));
        assertTrue(Files.isRegularFile(tempDir.resolve(RegressionLedger.HEALTH_MARKDOWN_FILE)));

        RegressionLedger reopened = new RegressionLedger(tempDir);
        assertEquals(10, reopened.cases().size());
        assertEquals(10, reopened.flags().size());
        assertEquals(2, reopened.bypasses().size());
        assertTrue(reopened.health().healthy());
    }

    @Test
    void convertingABypassUpdatesThePersistedRecord() {
        RegressionLedger ledger = new RegressionLedger(tempDir);
        ledger.recordBypasses(List.of(
                new RegressionHealth.BypassReport("bypass-1", "FLY", NOW, false)));

        assertTrue(ledger.convertBypass("bypass-1"));
        assertTrue(ledger.bypasses().get(0).converted());
        assertFalse(ledger.convertBypass("missing"));

        RegressionLedger reopened = new RegressionLedger(tempDir);
        assertTrue(reopened.bypasses().get(0).converted());
    }

    @Test
    void duplicateIdsAreIdempotent() {
        RegressionLedger ledger = new RegressionLedger(tempDir);
        RegressionCase first = regression("reg-1", "fp-1");

        ledger.recordCases(List.of(first, first));
        ledger.recordFlags(List.of(falsePositive("fp-1"), falsePositive("fp-1")));

        assertEquals(1, ledger.cases().size());
        assertEquals(1, ledger.flags().size());
    }

    private static RegressionCase regression(String id, String flagId) {
        return new RegressionCase(id, flagId, "traj-" + flagId, "fix-1", "", NOW);
    }

    private static FlagEvent falsePositive(String flagId) {
        return new FlagEvent(flagId, "run-1", "sim-1", PlayerType.LEGIT,
                "reach", null, null, 1.0, 10, "traj", "ev", true);
    }
}
