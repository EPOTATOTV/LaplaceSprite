package io.laplace.sprite.profile;

import java.util.List;

/**
 * spark 命令构造（15.3）：Paper 1.21 起服务端内置 spark，剖析轨不自己写采样器。
 *
 * <p>只负责拼命令行，不管下发；命令通道由 {@link SparkClient} 持有。</p>
 */
public final class SparkCommandFactory {

    /** profiler 默认采样窗口（秒），对齐文档的 --timeout 60。 */
    public static final int DEFAULT_PROFILER_TIMEOUT_SECONDS = 60;

    /** 尖刺阈值（毫秒）：只记录超过该时长的 tick。 */
    public static final int DEFAULT_SPIKE_THRESHOLD_MS = 100;

    private SparkCommandFactory() {
    }

    /** /spark tps：5s/10s/1m/5m/15m 多窗口 TPS 与 MSPT。 */
    public static SparkCommand tps() {
        return new SparkCommand("tps", List.of());
    }

    /** /spark health：内存 / GC / CPU / 磁盘。 */
    public static SparkCommand health() {
        return new SparkCommand("health", List.of());
    }

    /** /spark profiler start --timeout 60：采样剖面，产出分享链接与火焰图快照。 */
    public static SparkCommand profilerStart() {
        return profilerStart(DEFAULT_PROFILER_TIMEOUT_SECONDS);
    }

    public static SparkCommand profilerStart(int timeoutSeconds) {
        return new SparkCommand("profiler", List.of("start", "--timeout", Integer.toString(timeoutSeconds)));
    }

    /**
     * /spark profiler start --only-ticks-over 100：只采超过 100ms 的 tick，用于抓尖刺。
     */
    public static SparkCommand spikeProfiler() {
        return spikeProfiler(DEFAULT_SPIKE_THRESHOLD_MS);
    }

    public static SparkCommand spikeProfiler(int thresholdMs) {
        return new SparkCommand("profiler", List.of("start", "--only-ticks-over", Integer.toString(thresholdMs)));
    }

    /** /spark profiler stop：提前结束采样。 */
    public static SparkCommand profilerStop() {
        return new SparkCommand("profiler", List.of("stop"));
    }
}
