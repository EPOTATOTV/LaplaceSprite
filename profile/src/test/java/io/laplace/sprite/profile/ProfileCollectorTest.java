package io.laplace.sprite.profile;

import io.laplace.sprite.core.model.MetricSample;
import io.laplace.sprite.servers.MetricsAgent;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 报告联动与回归阈值（15.3）。 */
class ProfileCollectorTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private static MetricsAgent agent(String id, double tps, double mspt) {
        return new MetricsAgent(id, 5000L, step -> new MetricSample(T0.plusSeconds(5L * step), tps, mspt,
                512, 4096, 4.0, 25.0, 30.0, 0, 400));
    }

    @Test
    void regressionThresholdsAreExactAtBoundaries() {
        assertFalse(ProfileCollector.regressionSuspect(19.5, 12.0), "恰好 19.5 不算回归");
        assertTrue(ProfileCollector.regressionSuspect(19.49, 12.0));
        assertFalse(ProfileCollector.regressionSuspect(20.0, 60.0), "恰好 60ms 不算回归");
        assertTrue(ProfileCollector.regressionSuspect(20.0, 60.01));
        assertFalse(ProfileCollector.regressionSuspect(null, 60.0));
        assertTrue(ProfileCollector.regressionSuspect(null, 60.01));
        assertEquals(19.5, ProfileCollector.TPS_REGRESSION_THRESHOLD, 1.0e-9);
        assertEquals(60.0, ProfileCollector.MSPT_REGRESSION_THRESHOLD_MS, 1.0e-9);
    }

    @Test
    void offlineCollectorFallsBackToMetricsAgentWithoutLink() {
        ProfileCollector collector = ProfileCollector.offline("inst-1");
        ProfileReport report = collector.collect("inst-1");

        assertEquals("inst-1", report.instanceId());
        assertFalse(report.regressionSuspect());
        assertNull(report.sparkLink());
        assertFalse(report.hasSparkLink());
        assertTrue(report.attributionArtifact().isEmpty());
        assertEquals(20.0, report.tps5s(), 1.0e-9);
        assertNull(report.tps1m(), "回退模式不得伪造 1m 窗口");
        assertEquals(12.0, report.msptMax(), 1.0e-9);
        assertEquals(TpsRating.PERFECT, report.tpsRating());
        assertTrue(report.notes().stream().anyMatch(note -> note.contains("spark 通道不可用")));
    }

    @Test
    void tpsUnderThresholdMarksRegressionSuspect() {
        ProfileCollector collector = new ProfileCollector(
                new SparkClient(InMemoryCommandChannel.unavailable()), agent("inst-2", 19.4, 12.0), null);
        ProfileReport report = collector.collect("inst-2");

        assertTrue(report.regressionSuspect());
        assertNull(report.sparkLink(), "通道离线时必须留空，不许编造分享链接");
        assertEquals(TpsRating.SLIGHT_LAG, report.tpsRating());
        assertTrue(report.notes().stream().anyMatch(note -> note.contains("未取得 spark 分享链接")));
    }

    @Test
    void msptMaxOverThresholdMarksRegressionSuspectEvenWithFullTps() {
        ProfileCollector collector = new ProfileCollector(
                new SparkClient(InMemoryCommandChannel.unavailable()), agent("inst-3", 20.0, 61.0), null);
        ProfileReport report = collector.collect("inst-3");

        assertTrue(report.regressionSuspect());
        assertEquals(61.0, report.msptMax(), 1.0e-9);
        assertEquals(TpsRating.PERFECT, report.tpsRating());
    }

    @Test
    void regressionSuspectAttachesSparkLinkWhenChannelWorks() {
        InMemoryCommandChannel channel = InMemoryCommandChannel.available()
                .respond("/spark tps", "TPS from last 1m, 5m, 15m: 19.4, 19.3, 19.2")
                .respond("/spark health", "Heap: 2,048 MB / 4,096 MB")
                .respond("/spark profiler start --timeout 60", "results: https://spark.lucko.me/ZzYyXx99");
        ProfileCollector collector = new ProfileCollector(new SparkClient(channel), agent("inst-4", 20.0, 12.0),
                null);
        ProfileReport report = collector.collect("inst-4");

        assertTrue(report.regressionSuspect());
        assertEquals("https://spark.lucko.me/ZzYyXx99", report.sparkLink());
        assertEquals("https://spark.lucko.me/ZzYyXx99", report.attributionArtifact().orElseThrow());
        assertEquals(2048L, report.heapUsedMb());
        assertEquals(19.4, report.tps1m(), 1.0e-9);
    }

    @Test
    void noProfilerCallWhenNoRegressionSuspected() {
        InMemoryCommandChannel channel = InMemoryCommandChannel.available()
                .respond("/spark tps", "TPS from last 1m, 5m, 15m: 20.0, 20.0, 20.0")
                .respond("/spark health", "Heap: 1,024 MB / 4,096 MB");
        ProfileCollector collector = new ProfileCollector(new SparkClient(channel), agent("inst-5", 20.0, 10.0),
                null);
        ProfileReport report = collector.collect("inst-5");

        assertFalse(report.regressionSuspect());
        assertNull(report.sparkLink());
        assertFalse(channel.sentCommands().contains("/spark profiler start --timeout 60"),
                "没判回归就不该触发 60s 采样");
        assertEquals(List.of("/spark tps", "/spark health"), channel.sentCommands());
    }

    @Test
    void spikeTicksCollectSamplesOverHundredMs() {
        MetricsAgent spiky = new MetricsAgent("inst-6", 5000L,
                step -> new MetricSample(T0.plusSeconds(5L * step), 20.0, step == 2 ? 120.0 : 12.0,
                        512, 4096, 4.0, 25.0, 30.0, 0, 400));
        for (int i = 0; i < 5; i++) {
            spiky.sampleNow();
        }
        ProfileCollector collector = new ProfileCollector(new SparkClient(InMemoryCommandChannel.unavailable()),
                spiky, null);
        ProfileReport report = collector.collect("inst-6");

        assertEquals(List.of(2L), report.spikeTicks());
        assertEquals(120.0, report.msptMax(), 1.0e-9);
        assertTrue(report.regressionSuspect());
    }

    @Test
    void jfrRecorderRendersJcmdCommands() {
        Path output = Path.of("build", "jfr");
        JfrRecorder recorder = new JfrRecorder(output);
        Path target = output.resolve("laplace-inst-7.jfr");

        assertEquals("jcmd", recorder.jcmdBinary());
        assertEquals(target, recorder.targetFile("inst-7"));
        assertEquals("jcmd 4242 JFR.start name=laplace-inst-7 filename=%s settings=profile".formatted(target),
                recorder.startCommand(4242L, "inst-7"));
        assertEquals("jcmd 4242 JFR.dump name=laplace-inst-7 filename=%s".formatted(target),
                recorder.dumpCommand(4242L, "inst-7"));
        assertEquals("jcmd 4242 JFR.stop name=laplace-inst-7", recorder.stopCommand(4242L, "inst-7"));
        assertEquals("/opt/jdk/bin/jcmd", new JfrRecorder(output, "/opt/jdk/bin/jcmd").jcmdBinary());
    }

    @Test
    void loadTestCarriesJfrTargetFileIntoReport() {
        JfrRecorder recorder = new JfrRecorder(Path.of("build", "jfr"));
        ProfileCollector collector = new ProfileCollector(new SparkClient(InMemoryCommandChannel.unavailable()),
                agent("inst-8", 20.0, 12.0), recorder);

        Optional<String> start = collector.startJfr("inst-8", 9001L);
        assertTrue(start.isPresent());
        assertTrue(start.orElseThrow().contains("JFR.start"));
        assertEquals(Optional.of(Path.of("build", "jfr", "laplace-inst-8.jfr").toString()),
                collector.jfrFile("inst-8"));
        assertEquals(Path.of("build", "jfr", "laplace-inst-8.jfr").toString(),
                collector.collect("inst-8").jfrFile());
        assertTrue(collector.stopJfr("inst-8", 9001L).orElseThrow().contains("JFR.stop"));
        assertTrue(collector.dumpJfr("inst-8", 9001L).orElseThrow().contains("JFR.dump"));
    }

    @Test
    void noJfrConfiguredYieldsEmptyCommands() {
        ProfileCollector collector = ProfileCollector.offline("inst-9");
        assertTrue(collector.jfr().isEmpty());
        assertTrue(collector.startJfr("inst-9", 1L).isEmpty());
        assertTrue(collector.jfrFile("inst-9").isEmpty());
        assertNull(collector.collect("inst-9").jfrFile());
    }
}
