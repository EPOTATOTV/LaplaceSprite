package io.laplace.sprite.profile;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** spark 回执解析与离线降级（15.3）。 */
class SparkClientTest {

    private static final String TPS_FIVE_WINDOWS = """
            TPS from last 5s, 10s, 1m, 5m, 15m: 20.0, 19.98, 19.95, 19.9, 19.8
            Tick durations (min/med/95%ile/max ms) from last 5s, 10s, 1m, 5m, 15m: 0.9/12.4/28.6/75.1; 0.8/12.1/25.3/68.4; 0.7/11.9/24.8/62.2; 0.6/11.8/24.1/58.9; 0.5/11.7/23.9/57.3
            """;

    private static final String TPS_THREE_WINDOWS = """
            TPS from last 1m, 5m, 15m: 19.9, 19.8, 19.7
            """;

    private static final String HEALTH = """
            Health Report
            -- Memory --
            Heap: 1,024 MB / 4,096 MB
            GC pause p99: 12.5 ms
            GC pause max: 48.2 ms
            -- CPU --
            CPU: 35.5 %
            -- Disk --
            Disk: 62.0 %
            """;

    private static final String PROFILER_WITH_LINK = """
            Profiling has been started in the background.
            Profiling stopped, results: https://spark.lucko.me/AbCdEf1234
            """;

    @Test
    void parsesFiveWindowTpsAndTickDurations() {
        SparkTps tps = SparkClient.parseTps(TPS_FIVE_WINDOWS);
        assertTrue(tps.present());
        assertEquals(20.0, tps.tps5s(), 1.0e-9);
        assertEquals(19.98, tps.tps10s(), 1.0e-9);
        assertEquals(19.95, tps.tps1m(), 1.0e-9);
        assertEquals(19.9, tps.tps5m(), 1.0e-9);
        assertEquals(19.8, tps.tps15m(), 1.0e-9);
        assertEquals(19.95, tps.primaryTps(), 1.0e-9);
        assertEquals(0.5, tps.msptMin(), 1.0e-9);
        assertEquals(75.1, tps.msptMax(), 1.0e-9);
        assertEquals(11.98, tps.msptAvg(), 1.0e-9);
        assertEquals(5, tps.windows().size());
    }

    @Test
    void parsesThreeWindowTpsLeavingShortWindowsNull() {
        SparkTps tps = SparkClient.parseTps(TPS_THREE_WINDOWS);
        assertNull(tps.tps5s());
        assertNull(tps.tps10s());
        assertEquals(19.9, tps.tps1m(), 1.0e-9);
        assertEquals(19.8, tps.tps5m(), 1.0e-9);
        assertEquals(19.7, tps.tps15m(), 1.0e-9);
        assertEquals(19.9, tps.primaryTps(), 1.0e-9);
        assertNull(tps.msptMax());
    }

    @Test
    void unparseableTpsTextYieldsEmpty() {
        assertFalse(SparkClient.parseTps("nothing useful here").present());
        assertFalse(SparkClient.parseTps("").present());
        assertNull(SparkTps.empty().primaryTps());
    }

    @Test
    void parsesHealthFigures() {
        SparkHealth health = SparkClient.parseHealth(HEALTH);
        assertEquals(1024L, health.heapUsedMb().longValue());
        assertEquals(4096L, health.heapMaxMb().longValue());
        assertEquals(12.5, health.gcPauseP99Ms(), 1.0e-9);
        assertEquals(48.2, health.gcPauseMaxMs(), 1.0e-9);
        assertEquals(35.5, health.cpuPercent(), 1.0e-9);
        assertEquals(62.0, health.diskPercent(), 1.0e-9);
    }

    @Test
    void missingHealthLabelsStayNull() {
        SparkHealth health = SparkClient.parseHealth("Heap: 512 MB / 2,048 MB");
        assertEquals(512L, health.heapUsedMb().longValue());
        assertEquals(2048L, health.heapMaxMb().longValue());
        assertNull(health.gcPauseP99Ms());
        assertNull(health.cpuPercent());
        assertNull(health.diskPercent());
    }

    @Test
    void extractsShareLinkOnlyWhenPresent() {
        assertEquals(Optional.of("https://spark.lucko.me/AbCdEf1234"),
                SparkClient.parseProfilerLink(PROFILER_WITH_LINK));
        assertTrue(SparkClient.parseProfilerLink("Profiling has been started.").isEmpty());
        assertTrue(SparkClient.parseProfilerLink(null).isEmpty());
    }

    @Test
    void issuesCommandsThroughChannelAndParsesReplies() {
        InMemoryCommandChannel channel = InMemoryCommandChannel.available()
                .respond("/spark tps", TPS_FIVE_WINDOWS)
                .respond("/spark health", HEALTH)
                .respond("/spark profiler start --timeout 60", PROFILER_WITH_LINK);
        SparkClient client = new SparkClient(channel);

        assertTrue(client.available());
        assertEquals(19.95, client.tps().tps1m(), 1.0e-9);
        assertEquals(1024L, client.health().heapUsedMb().longValue());
        assertEquals(Optional.of("https://spark.lucko.me/AbCdEf1234"), client.profilerLink());
        assertEquals(List.of("/spark tps", "/spark health", "/spark profiler start --timeout 60"),
                channel.sentCommands());
    }

    @Test
    void unavailableChannelDegradesWithoutFabricatingAnything() {
        InMemoryCommandChannel channel = InMemoryCommandChannel.unavailable();
        SparkClient client = new SparkClient(channel);

        SparkTps tps = client.tps();
        assertFalse(client.available());
        assertFalse(tps.present());
        assertNull(tps.primaryTps());
        assertFalse(client.health().present());
        assertTrue(client.profilerLink().isEmpty());
        assertTrue(client.spikeProfilerLink().isEmpty());
        assertEquals(4, channel.sentCommands().size(), "tps / health / profiler / 尖刺各下发一次，且都没回执");
    }

    @Test
    void channelExceptionIsSurfacedAsUnavailable() {
        CommandChannel failing = command -> {
            throw new CommandChannelUnavailableException("no rcon");
        };
        SparkClient client = new SparkClient(failing);
        assertFalse(client.tps().present());
        assertFalse(client.available());
    }

    @Test
    void directChannelCallsExposeUnavailability() {
        assertThrows(CommandChannelUnavailableException.class,
                () -> InMemoryCommandChannel.unavailable().send("/spark tps"));
        assertEquals("", InMemoryCommandChannel.available().send("/spark tps"));
    }
}
