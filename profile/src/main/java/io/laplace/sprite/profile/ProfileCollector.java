package io.laplace.sprite.profile;

import io.laplace.sprite.core.model.MetricSample;
import io.laplace.sprite.servers.MetricsAgent;
import io.laplace.sprite.servers.SyntheticProfile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 剖析采集（15.3）：每条用例结束跑一遍 tps + health，按阈值判「性能回归嫌疑」，
 * 压测类用例另落 JFR。
 *
 * <p>spark 通道不可用时整条链路走降级：Tick 与 JVM 指标回退到 {@link MetricsAgent} 采样，
 * spark 链接为 null 并写进 note。降级不等于失败，但报告里必须看得见。</p>
 */
public final class ProfileCollector {

    /** 性能回归嫌疑的 TPS 阈值：低于 19.5 即命中（等于 19.5 不算）。 */
    public static final double TPS_REGRESSION_THRESHOLD = 19.5;

    /** 性能回归嫌疑的 MSPT max 阈值：超过 60ms 即命中（等于 60ms 不算）。 */
    public static final double MSPT_REGRESSION_THRESHOLD_MS = 60.0;

    /** 尖刺判定阈值：单次采样 MSPT 超过该值记为尖刺，对齐 --only-ticks-over 100。 */
    public static final double SPIKE_MSPT_THRESHOLD_MS = 100.0;

    private final SparkClient spark;
    private final MetricsAgent metrics;
    private final JfrRecorder jfr;
    private volatile String pendingJfrFile;

    public ProfileCollector(SparkClient spark, MetricsAgent metrics, JfrRecorder jfr) {
        this.spark = Objects.requireNonNull(spark, "spark");
        this.metrics = metrics;
        this.jfr = jfr;
    }

    /** 全离线采集器：无 spark 通道、无 JFR，指标来自合成画像。 */
    public static ProfileCollector offline(String instanceId) {
        return new ProfileCollector(new SparkClient(InMemoryCommandChannel.unavailable()),
                MetricsAgent.synthetic(instanceId, SyntheticProfile.steady()), null);
    }

    public SparkClient spark() {
        return spark;
    }

    public Optional<JfrRecorder> jfr() {
        return Optional.ofNullable(jfr);
    }

    /**
     * 回归嫌疑判定：TPS 低于 19.5 或 MSPT max 超过 60ms。TPS 取不到时只按 MSPT 判。
     */
    public static boolean regressionSuspect(Double tps, double msptMax) {
        if (tps != null && tps < TPS_REGRESSION_THRESHOLD) {
            return true;
        }
        return msptMax > MSPT_REGRESSION_THRESHOLD_MS;
    }

    /** 采集一份报告。 */
    public ProfileReport collect(String instanceId) {
        List<String> notes = new ArrayList<>();
        SparkTps tps = spark.tps();
        boolean channelAvailable = spark.available();
        SparkHealth health = spark.health();

        List<MetricSample> samples = samples();
        MetricSample latest = samples.isEmpty() ? null : samples.get(samples.size() - 1);

        double msptAvg = tps.msptAvg() != null ? tps.msptAvg() : averageMspt(samples);
        double msptMin = tps.msptMin() != null ? tps.msptMin() : minMspt(samples);
        double msptMax = tps.msptMax() != null ? tps.msptMax() : maxMspt(samples);

        Double tps5s = tps.tps5s() != null ? tps.tps5s() : sampleTps(latest);
        Double effectiveTps = tps.primaryTps() != null ? tps.primaryTps() : sampleTps(latest);

        long heapUsedMb = health.heapUsedMb() != null ? health.heapUsedMb()
                : (latest == null ? 0L : latest.heapUsedMb());
        long heapMaxMb = health.heapMaxMb() != null ? health.heapMaxMb()
                : (latest == null ? 0L : latest.heapMaxMb());
        Double gcPauseMaxMs = health.gcPauseMaxMs() != null ? health.gcPauseMaxMs()
                : (latest == null ? null : latest.gcPauseMs());
        Double cpuPercent = health.cpuPercent() != null ? health.cpuPercent()
                : (latest == null ? null : latest.cpuPercent());

        boolean suspect = regressionSuspect(effectiveTps, msptMax);
        String sparkLink = null;
        if (suspect) {
            // 只有判定回归时才触发 60s 采样，避免每条用例都留一份剖面
            sparkLink = spark.profilerLink().orElse(null);
            if (sparkLink == null) {
                notes.add("回归嫌疑成立但未取得 spark 分享链接（通道离线或回执未含链接），不做推测");
            }
        }

        if (!channelAvailable) {
            notes.add("spark 通道不可用，指标回退到 MetricsAgent 采样：5s 窗口取最近一次采样，"
                    + "10s/1m/5m/15m 窗口未采集");
        } else if (!tps.present() && !health.present()) {
            notes.add("spark 回执未解析出 TPS / health 字段，相关字段留空");
        }
        if (effectiveTps == null) {
            notes.add("未取得任何 TPS 值，评级按最低档给出");
        }
        if (suspect) {
            notes.add("TPS %s / MSPT max %.1fms 越过回归阈值（TPS<%.1f 或 MSPT max>%.1fms），标记性能回归嫌疑"
                    .formatted(formatTps(effectiveTps), msptMax, TPS_REGRESSION_THRESHOLD,
                            MSPT_REGRESSION_THRESHOLD_MS));
        }

        return new ProfileReport(instanceId, Instant.now(), tps5s, tps.tps10s(), tps.tps1m(), tps.tps5m(),
                tps.tps15m(), msptAvg, msptMin, msptMax, heapUsedMb, heapMaxMb, health.gcPauseP99Ms(),
                gcPauseMaxMs, cpuPercent, health.diskPercent(), sparkLink, pendingJfrFile,
                spikeTicks(samples), suspect, TpsRating.of(effectiveTps == null ? 0.0 : effectiveTps), notes);
    }

    /** 单独取一份采样剖面链接（方法/插件级归因 artifact）。 */
    public Optional<String> captureAttribution() {
        return spark.profilerLink();
    }

    /** 压测类用例开录：渲染 JFR.start 命令并记住目标文件路径。 */
    public Optional<String> startJfr(String instanceId, long pid) {
        if (jfr == null) {
            return Optional.empty();
        }
        pendingJfrFile = jfr.targetFile(instanceId).toString();
        return Optional.of(jfr.startCommand(pid, instanceId));
    }

    /** 用例中途落一份现场。 */
    public Optional<String> dumpJfr(String instanceId, long pid) {
        return jfr == null ? Optional.empty() : Optional.of(jfr.dumpCommand(pid, instanceId));
    }

    /** 用例结束收尾。 */
    public Optional<String> stopJfr(String instanceId, long pid) {
        return jfr == null ? Optional.empty() : Optional.of(jfr.stopCommand(pid, instanceId));
    }

    /** JFR 目标文件路径（未配置录制器时为空）。 */
    public Optional<String> jfrFile(String instanceId) {
        return jfr == null ? Optional.empty() : Optional.of(jfr.targetFile(instanceId).toString());
    }

    private List<MetricSample> samples() {
        if (metrics == null) {
            return List.of();
        }
        if (metrics.history().isEmpty()) {
            metrics.sampleNow();
        }
        return metrics.history();
    }

    /** 尖刺专项：MSPT 超过阈值的采样序号。 */
    private static List<Long> spikeTicks(List<MetricSample> samples) {
        List<Long> spikes = new ArrayList<>();
        for (int i = 0; i < samples.size(); i++) {
            if (samples.get(i).mspt() > SPIKE_MSPT_THRESHOLD_MS) {
                spikes.add((long) i);
            }
        }
        return spikes;
    }

    private static Double sampleTps(MetricSample sample) {
        return sample == null ? null : sample.tps();
    }

    private static double averageMspt(List<MetricSample> samples) {
        if (samples.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (MetricSample sample : samples) {
            sum += sample.mspt();
        }
        return sum / samples.size();
    }

    private static double minMspt(List<MetricSample> samples) {
        double min = Double.MAX_VALUE;
        for (MetricSample sample : samples) {
            min = Math.min(min, sample.mspt());
        }
        return samples.isEmpty() ? 0.0 : min;
    }

    private static double maxMspt(List<MetricSample> samples) {
        double max = 0.0;
        for (MetricSample sample : samples) {
            max = Math.max(max, sample.mspt());
        }
        return max;
    }

    private static String formatTps(Double tps) {
        return tps == null ? "未取得" : "%.1f".formatted(tps);
    }
}
