package io.laplace.sprite.servers;

import io.laplace.sprite.core.model.MetricSample;
import io.laplace.sprite.core.model.RunStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 长稳看门狗（12.3 ④ / 12.6）。 */
class LongRunWatchdogTest {

    private static final Instant ORIGIN = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void sustainedTpsBreachOverFiveMinutesFails() {
        LongRunWatchdog watchdog = new LongRunWatchdog();
        for (int step = 0; step <= 80; step++) {
            watchdog.record(sample(step, 15.0));
        }
        assertTrue(watchdog.sustainedTpsBreach(), "TPS 15 持续 400 秒应判长稳跌破");
        assertEquals(RunStatus.FAILED, watchdog.verdict());
        assertTrue(watchdog.notices().stream().anyMatch(notice -> notice.contains("TPS 低于")));
    }

    @Test
    void briefDropIsNotSustained() {
        LongRunWatchdog watchdog = new LongRunWatchdog();
        for (int step = 0; step <= 80; step++) {
            watchdog.record(sample(step, step < 3 ? 15.0 : 20.0));
        }
        assertFalse(watchdog.sustainedTpsBreach(), "10 秒抖动不算长稳跌破");
    }

    @Test
    void healthyRunStillRunningBeforePlannedDuration() {
        LongRunWatchdog watchdog = new LongRunWatchdog();
        for (int step = 0; step <= 12; step++) {
            watchdog.record(sample(step, 20.0));
        }
        assertEquals(Duration.ofSeconds(60), watchdog.observedDuration());
        assertEquals(RunStatus.RUNNING, watchdog.verdict());
    }

    @Test
    void oomAlwaysFails() {
        LongRunWatchdog watchdog = new LongRunWatchdog();
        watchdog.record(sample(0, 20.0));
        watchdog.recordOom("java.lang.OutOfMemoryError: Java heap space");
        assertTrue(watchdog.oom());
        assertEquals(RunStatus.FAILED, watchdog.verdict());
        assertTrue(watchdog.notices().stream().anyMatch(notice -> notice.contains("OOM")));
    }

    @Test
    void completedHealthyRunPasses() {
        LongRunWatchdog watchdog = new LongRunWatchdog(19.5, Duration.ofMinutes(5),
                Duration.ofSeconds(30));
        for (int step = 0; step <= 10; step++) {
            watchdog.record(sample(step, 20.0));
        }
        assertEquals(RunStatus.PASSED, watchdog.verdict());
    }

    private static MetricSample sample(int step, double tps) {
        return new MetricSample(ORIGIN.plusSeconds(step * 5L), tps, 12.0, 1024, 4096, 3.0, 30.0, 40.0,
                10, 500);
    }
}
