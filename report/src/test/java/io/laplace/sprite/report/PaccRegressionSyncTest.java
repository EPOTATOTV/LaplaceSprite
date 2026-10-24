package io.laplace.sprite.report;

import io.laplace.sprite.core.model.DetectionBreakdown;
import io.laplace.sprite.core.model.FlagEvent;
import io.laplace.sprite.core.model.MetricSet;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.model.RunStatus;
import io.laplace.sprite.core.model.TestRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PACC 误报自动入回归库（L2-06）。 */
class PaccRegressionSyncTest {

    @TempDir
    Path tempDir;

    @Test
    void falsePositivesBecomeRegressionCasesWithTheirTrajectory() {
        TestRun run = run(FlagEvent.falsePositive("run-1", "sim-1", "reach", 12, "traj-12", "ev-12"));

        PaccRegressionSync.Summary summary = new PaccRegressionSync(tempDir).sync(List.of(run));

        assertEquals(1, summary.falsePositives());
        assertEquals(1, summary.addedCases());
        assertEquals(0, summary.skippedWithoutTrajectory());

        RegressionLedger ledger = new RegressionLedger(tempDir.resolve("regression"));
        assertEquals(1, ledger.flags().size());
        assertEquals(1, ledger.cases().size());
        assertEquals("traj-12", ledger.cases().get(0).trajectoryRef());
        assertTrue(Files.isRegularFile(tempDir.resolve("regression").resolve(RegressionLedger.HEALTH_FILE)));
    }

    @Test
    void repeatedSyncIsIdempotent() {
        TestRun run = run(FlagEvent.falsePositive("run-1", "sim-1", "speed", 3, "traj-3", "ev-3"));
        PaccRegressionSync sync = new PaccRegressionSync(tempDir);

        sync.sync(List.of(run));
        PaccRegressionSync.Summary second = sync.sync(List.of(run));

        assertEquals(1, second.falsePositives());
        assertEquals(0, second.addedCases());
        assertEquals(1, new RegressionLedger(tempDir.resolve("regression")).cases().size());
    }

    @Test
    void flagWithoutTrajectoryIsRecordedButNotTurnedIntoACase() {
        TestRun run = run(FlagEvent.falsePositive("run-1", "sim-2", "fly", 7, null, "ev-7"));

        PaccRegressionSync.Summary summary = new PaccRegressionSync(tempDir).sync(List.of(run));

        assertEquals(1, summary.falsePositives());
        assertEquals(0, summary.addedCases());
        assertEquals(1, summary.skippedWithoutTrajectory());
        assertEquals(1, new RegressionLedger(tempDir.resolve("regression")).flags().size());
        assertTrue(new RegressionLedger(tempDir.resolve("regression")).cases().isEmpty());
    }

    private static TestRun run(FlagEvent flag) {
        MetricSet metrics = new MetricSet(100, 1, 0, 0, 0, 0, true, 1.0);
        return new TestRun("run-1", "detection-engine", "legit",
                PlatformKey.of(PlatformFamily.PAPER, "26.3"), metrics, DetectionBreakdown.empty(),
                List.of(), List.of(flag), RunStatus.PASSED, 20261023L, Instant.now(), 1000L);
    }
}
