package io.laplace.sprite.profile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** spark 命令拼装（15.3）：命令行必须与文档口径逐字对齐。 */
class SparkCommandFactoryTest {

    @Test
    void tpsAndHealthCommands() {
        assertEquals("/spark tps", SparkCommandFactory.tps().commandLine());
        assertEquals("/spark health", SparkCommandFactory.health().commandLine());
        assertEquals("tps", SparkCommandFactory.tps().subcommand());
        assertTrue(SparkCommandFactory.tps().args().isEmpty());
    }

    @Test
    void profilerStartCarriesTimeoutSixty() {
        assertEquals("/spark profiler start --timeout 60", SparkCommandFactory.profilerStart().commandLine());
        assertEquals("/spark profiler start --timeout 30", SparkCommandFactory.profilerStart(30).commandLine());
    }

    @Test
    void spikeProfilerOnlyKeepsTicksOverHundredMs() {
        assertEquals("/spark profiler start --only-ticks-over 100",
                SparkCommandFactory.spikeProfiler().commandLine());
        assertEquals("/spark profiler start --only-ticks-over 250",
                SparkCommandFactory.spikeProfiler(250).commandLine());
    }

    @Test
    void profilerStop() {
        assertEquals("/spark profiler stop", SparkCommandFactory.profilerStop().commandLine());
    }
}
