package io.laplace.sprite.servers;

import io.laplace.sprite.core.model.MetricSample;
import io.laplace.sprite.core.model.RunStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 长稳看门狗（12.3 ④ / 12.6）：盯时长、OOM 与 TPS 长时跌破。
 *
 * <p>「TPS 持续低于阈值超过 5 分钟」才判失败，偶发抖动不算——阈值与时长都可配置。</p>
 */
public final class LongRunWatchdog {

    public static final double DEFAULT_TPS_THRESHOLD = 19.5;
    public static final Duration DEFAULT_SUSTAINED = Duration.ofMinutes(5);
    public static final Duration DEFAULT_PLANNED = Duration.ofHours(24);

    private final double tpsThreshold;
    private final Duration sustained;
    private final Duration plannedDuration;
    private final List<String> notices = new ArrayList<>();

    private MetricSample first;
    private MetricSample last;
    private Instant breachStart;
    private Instant breachEnd;
    private Duration longestBreach = Duration.ZERO;
    private String oomMessage;

    public LongRunWatchdog() {
        this(DEFAULT_TPS_THRESHOLD, DEFAULT_SUSTAINED, DEFAULT_PLANNED);
    }

    public LongRunWatchdog(double tpsThreshold, Duration sustained, Duration plannedDuration) {
        this.tpsThreshold = tpsThreshold;
        this.sustained = sustained;
        this.plannedDuration = plannedDuration;
    }

    public void record(MetricSample sample) {
        if (first == null) {
            first = sample;
        }
        last = sample;
        if (sample.tps() < tpsThreshold) {
            if (breachStart == null) {
                breachStart = sample.at();
            }
            breachEnd = sample.at();
            Duration span = Duration.between(breachStart, breachEnd);
            if (span.compareTo(longestBreach) > 0) {
                longestBreach = span;
            }
        } else {
            breachStart = null;
            breachEnd = null;
        }
    }

    public void recordOom(String message) {
        oomMessage = message;
        notices.add("OOM: " + message);
    }

    public boolean oom() {
        return oomMessage != null;
    }

    public Duration observedDuration() {
        if (first == null || last == null) {
            return Duration.ZERO;
        }
        return Duration.between(first.at(), last.at());
    }

    public Duration longestTpsBreach() {
        return longestBreach;
    }

    /** TPS 低于阈值的持续时间是否超过设定窗口。 */
    public boolean sustainedTpsBreach() {
        return longestBreach.compareTo(sustained) >= 0 && !longestBreach.isZero();
    }

    /** 判定：OOM 或长时跌破即失败；未跑满计划时长算还在跑。 */
    public RunStatus verdict() {
        if (oom()) {
            return RunStatus.FAILED;
        }
        if (sustainedTpsBreach()) {
            return RunStatus.FAILED;
        }
        if (observedDuration().compareTo(plannedDuration) < 0) {
            return RunStatus.RUNNING;
        }
        return RunStatus.PASSED;
    }

    public List<String> notices() {
        List<String> all = new ArrayList<>(notices);
        if (sustainedTpsBreach()) {
            all.add("TPS 低于 %.1f 持续 %d 秒".formatted(tpsThreshold, longestBreach.toSeconds()));
        }
        return List.copyOf(all);
    }
}
