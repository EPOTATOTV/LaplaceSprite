package io.laplace.sprite.report;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PACC 趋势历史（L-10）：追加、同版本覆盖与回归判定。 */
class TrendHistoryTest {

    private static final Instant NOW = Instant.parse("2026-10-18T00:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void entriesSurviveRestartAndKeepInsertionOrder() {
        Path file = tempDir.resolve("trend-history.json");
        TrendHistory history = new TrendHistory(file);
        history.append(new DetectionTrend.Point("1.3.0", 0.0001, 0.98), NOW);
        history.append(new DetectionTrend.Point("1.4.0", 0.0000, 0.99), NOW);
        history.save();

        TrendHistory reopened = new TrendHistory(file);
        assertEquals(List.of("1.3.0", "1.4.0"),
                reopened.entries().stream().map(TrendHistory.Entry::version).toList());
        assertEquals(2, reopened.trend().points().size());
        assertTrue(Files.isRegularFile(file));
    }

    @Test
    void rerunningTheSameVersionReplacesTheEntry() {
        TrendHistory history = new TrendHistory(tempDir.resolve("trend-history.json"));
        history.append(new DetectionTrend.Point("1.4.0", 0.0002, 0.97), NOW);
        history.append(new DetectionTrend.Point("1.4.0", 0.0000, 0.99), NOW.plusSeconds(60));

        assertEquals(1, history.entries().size());
        assertEquals(0.0, history.entries().get(0).falsePositiveRate(), 1e-9);
        assertEquals(0.99, history.entries().get(0).detectionRate(), 1e-9);
    }

    @Test
    void regressionIsDetectedWhenFalsePositiveRisesOrDetectionDrops() {
        TrendHistory history = new TrendHistory(tempDir.resolve("trend-history.json"));
        history.append(new DetectionTrend.Point("1.3.0", 0.0001, 0.99), NOW);
        history.append(new DetectionTrend.Point("1.4.0", 0.0005, 0.97), NOW);

        TrendHistory.Regression regression = history.compareToPrevious(TrendHistory.Thresholds.defaults());

        assertTrue(regression.regressed());
        assertEquals(2, regression.reasons().size());
        assertTrue(regression.reasons().get(0).contains("误报率上升"));
        assertTrue(regression.reasons().get(1).contains("检出率下降"));
    }

    @Test
    void smallChangesAndSingleEntryAreNotRegressions() {
        TrendHistory single = new TrendHistory(tempDir.resolve("single.json"));
        single.append(new DetectionTrend.Point("1.4.0", 0.0001, 0.99), NOW);
        assertFalse(single.compareToPrevious(TrendHistory.Thresholds.defaults()).regressed());

        single.append(new DetectionTrend.Point("1.4.1", 0.00015, 0.989), NOW);
        assertFalse(single.compareToPrevious(TrendHistory.Thresholds.defaults()).regressed());
    }
}
