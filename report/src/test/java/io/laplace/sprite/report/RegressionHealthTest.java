package io.laplace.sprite.report;

import io.laplace.sprite.core.model.FlagEvent;
import io.laplace.sprite.core.model.PlayerType;
import io.laplace.sprite.core.model.RegressionCase;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 回归库健康度（L-03）：规模、转化率、复现覆盖率与超期报告。 */
class RegressionHealthTest {

    private static final Instant NOW = Instant.parse("2026-10-18T00:00:00Z");
    private final RegressionHealth.Thresholds thresholds =
            new RegressionHealth.Thresholds(2, 0.8, 1.0, Duration.ofDays(30));

    @Test
    void healthyLibraryMeetsEveryThreshold() {
        List<RegressionCase> cases = List.of(regression("fp-1"), regression("fp-2"));
        List<FlagEvent> flags = List.of(falsePositive("fp-1"), falsePositive("fp-2"));
        List<RegressionHealth.BypassReport> bypasses = List.of(
                new RegressionHealth.BypassReport("bypass-1", "SPEED", NOW.minus(Duration.ofDays(3)), true),
                new RegressionHealth.BypassReport("bypass-2", "REACH", NOW.minus(Duration.ofDays(10)), true));

        RegressionHealth.Health health = RegressionHealth.evaluate(cases, flags, bypasses, thresholds, NOW);

        assertTrue(health.healthy(), health.violations().toString());
        assertEquals(1.0, health.bypassConversionRate());
        assertEquals(1.0, health.falsePositiveCoverage());
        assertEquals(0, health.overdueBypasses());
    }

    @Test
    void missingRegressionCaseForAFalsePositiveFailsCoverage() {
        List<RegressionCase> cases = List.of(regression("fp-1"));
        List<FlagEvent> flags = List.of(falsePositive("fp-1"), falsePositive("fp-2"));

        RegressionHealth.Health health = RegressionHealth.evaluate(cases, flags, List.of(), thresholds, NOW);

        assertFalse(health.healthy());
        assertEquals(0.5, health.falsePositiveCoverage());
        assertTrue(health.violations().stream().anyMatch(line -> line.contains("误报复现覆盖率")));
    }

    @Test
    void overdueUnconvertedBypassIsReported() {
        List<RegressionHealth.BypassReport> bypasses = List.of(
                new RegressionHealth.BypassReport("old", "FLY", NOW.minus(Duration.ofDays(31)), false),
                new RegressionHealth.BypassReport("new", "FLY", NOW.minus(Duration.ofDays(5)), false));

        RegressionHealth.Health health = RegressionHealth.evaluate(
                List.of(regression("a"), regression("b")), List.of(), bypasses, thresholds, NOW);

        assertFalse(health.healthy());
        assertEquals(1, health.overdueBypasses());
        assertEquals(0.0, health.bypassConversionRate());
        assertTrue(health.violations().stream().anyMatch(line -> line.contains("超过 30 天")));
    }

    @Test
    void emptyInputsDoNotPretendToBeAFailureOfCoverage() {
        RegressionHealth.Health health = RegressionHealth.evaluate(
                List.of(regression("a"), regression("b")), List.of(), List.of(), thresholds, NOW);

        assertTrue(health.healthy(), "没有误报和绕过报告时不应凭空判覆盖率不达标");
        assertEquals(1.0, health.falsePositiveCoverage());
    }

    private static RegressionCase regression(String flagId) {
        return new RegressionCase("reg-" + flagId, flagId, "traj-" + flagId, "fix-1", "", NOW);
    }

    private static FlagEvent falsePositive(String flagId) {
        return new FlagEvent(flagId, "run-1", "sim-1", PlayerType.LEGIT,
                "reach", null, null, 1.0, 10, "traj", "ev", true);
    }
}
