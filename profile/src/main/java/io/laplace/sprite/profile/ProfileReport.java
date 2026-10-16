package io.laplace.sprite.profile;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 一次剖析报告（15.3）。四类指标固定采集：Tick 健康、JVM 资源、归因 artifact、尖刺专项。
 *
 * <p>spark 不可用时对应字段为 null，指标回退到 MetricsAgent 采样，报告与 note 里都会写明，
 * 不把回退值当作 spark 结果。</p>
 *
 * @param instanceId       实例 id
 * @param collectedAt      采集时间
 * @param tps5s            5 秒窗口 TPS
 * @param tps10s           10 秒窗口 TPS
 * @param tps1m            1 分钟窗口 TPS
 * @param tps5m            5 分钟窗口 TPS
 * @param tps15m           15 分钟窗口 TPS
 * @param msptAvg          tick 耗时均值（ms）
 * @param msptMin          tick 耗时最小值（ms）
 * @param msptMax          tick 耗时最大值（ms）
 * @param heapUsedMb       堆占用（MB）
 * @param heapMaxMb        堆上限（MB）
 * @param gcPauseP99Ms     GC 停顿 p99（ms）
 * @param gcPauseMaxMs     GC 停顿最大值（ms）
 * @param cpuPercent       CPU 占用（%）
 * @param diskPercent      磁盘占用（%）
 * @param sparkLink        spark 采样剖面分享链接；降级时为 null
 * @param jfrFile          JFR 落盘文件路径；未录制时为 null
 * @param spikeTicks       尖刺 tick 序号
 * @param regressionSuspect 是否标记性能回归嫌疑
 * @param tpsRating        TPS 评级
 * @param notes            采集口径与降级说明
 */
public record ProfileReport(
        String instanceId,
        Instant collectedAt,
        Double tps5s,
        Double tps10s,
        Double tps1m,
        Double tps5m,
        Double tps15m,
        double msptAvg,
        double msptMin,
        double msptMax,
        long heapUsedMb,
        long heapMaxMb,
        Double gcPauseP99Ms,
        Double gcPauseMaxMs,
        Double cpuPercent,
        Double diskPercent,
        String sparkLink,
        String jfrFile,
        List<Long> spikeTicks,
        boolean regressionSuspect,
        TpsRating tpsRating,
        List<String> notes) {

    public ProfileReport {
        spikeTicks = spikeTicks == null ? List.of() : List.copyOf(spikeTicks);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    /** 是否拿到了 spark 分享链接（方法/插件级归因 artifact）。 */
    public boolean hasSparkLink() {
        return sparkLink != null;
    }

    /** 归因 artifact：spark 链接；降级时为空。 */
    public Optional<String> attributionArtifact() {
        return Optional.ofNullable(sparkLink);
    }

    /** 视角切分（15.3）。 */
    public ProfileView forViewpoint(ProfileViewpoint viewpoint) {
        return ProfileView.of(this, viewpoint);
    }

    /** 带上 JFR 文件路径的副本，用于压测类用例。 */
    public ProfileReport withJfrFile(String path) {
        return new ProfileReport(instanceId, collectedAt, tps5s, tps10s, tps1m, tps5m, tps15m, msptAvg,
                msptMin, msptMax, heapUsedMb, heapMaxMb, gcPauseP99Ms, gcPauseMaxMs, cpuPercent, diskPercent,
                sparkLink, path, spikeTicks, regressionSuspect, tpsRating, notes);
    }
}
