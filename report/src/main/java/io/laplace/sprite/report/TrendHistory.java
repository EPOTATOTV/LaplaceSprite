package io.laplace.sprite.report;

import io.laplace.sprite.core.io.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * PACC 趋势历史（升级实施文档 L-10）：把每次夜间结果追加到同一份时间线。
 *
 * <p>同版本重跑覆盖原条目，版本按首次出现顺序保留；趋势图由完整历史渲染，而不是只画当晚一个点。
 * 回归判定只比较最后两条：误报率上升超过阈值或检出率下降超过阈值即判回归。</p>
 */
public final class TrendHistory {

    /**
     * 一条历史记录。
     *
     * @param version          版本
     * @param falsePositiveRate 误报率
     * @param detectionRate    检出率
     * @param recordedAt       记录时间
     */
    public record Entry(String version, double falsePositiveRate, double detectionRate, Instant recordedAt) {

        public Entry {
            if (version == null || version.isBlank()) {
                throw new IllegalArgumentException("版本号不能为空");
            }
            if (falsePositiveRate < 0 || falsePositiveRate > 1 || detectionRate < 0 || detectionRate > 1) {
                throw new IllegalArgumentException("误报率 / 检出率必须在 0–1 之间: " + version);
            }
            Objects.requireNonNull(recordedAt, "recordedAt");
        }
    }

    /**
     * 回归阈值。
     *
     * @param maxFalsePositiveIncrease 误报率允许的最大上升
     * @param maxDetectionDrop        检出率允许的最大下降
     */
    public record Thresholds(double maxFalsePositiveIncrease, double maxDetectionDrop) {

        public Thresholds {
            if (maxFalsePositiveIncrease < 0 || maxDetectionDrop < 0) {
                throw new IllegalArgumentException("回归阈值不能为负");
            }
        }

        public static Thresholds defaults() {
            return new Thresholds(0.0001, 0.01);
        }
    }

    /**
     * 回归判定。
     *
     * @param regressed 是否回归
     * @param reasons   原因
     */
    public record Regression(boolean regressed, List<String> reasons) {

        public Regression {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }

        public static Regression none() {
            return new Regression(false, List.of());
        }
    }

    private final Path file;
    private final List<Entry> entries = new ArrayList<>();

    public TrendHistory(Path file) {
        this.file = Objects.requireNonNull(file, "file");
        entries.addAll(load());
    }

    /** 追加重跑结果；同版本覆盖原位置。 */
    public synchronized Entry append(DetectionTrend.Point point) {
        return append(point, Instant.now());
    }

    public synchronized Entry append(DetectionTrend.Point point, Instant at) {
        Entry entry = new Entry(point.version(), point.falsePositiveRate(), point.detectionRate(), at);
        int index = indexOf(point.version());
        if (index >= 0) {
            entries.set(index, entry);
        } else {
            entries.add(entry);
        }
        return entry;
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public DetectionTrend trend() {
        return DetectionTrend.ofPoints(entries.stream()
                .map(entry -> new DetectionTrend.Point(entry.version(), entry.falsePositiveRate(),
                        entry.detectionRate()))
                .toList());
    }

    /** 与上一条比较；少于两条时不判回归。 */
    public Regression compareToPrevious(Thresholds thresholds) {
        if (entries.size() < 2) {
            return Regression.none();
        }
        Entry previous = entries.get(entries.size() - 2);
        Entry current = entries.get(entries.size() - 1);
        List<String> reasons = new ArrayList<>();
        double fprIncrease = current.falsePositiveRate() - previous.falsePositiveRate();
        double detectionDrop = previous.detectionRate() - current.detectionRate();
        if (fprIncrease > thresholds.maxFalsePositiveIncrease()) {
            reasons.add(String.format(Locale.ROOT, "误报率上升 %.4f%%（%s → %s）",
                    fprIncrease * 100, previous.version(), current.version()));
        }
        if (detectionDrop > thresholds.maxDetectionDrop()) {
            reasons.add(String.format(Locale.ROOT, "检出率下降 %.2f%%（%s → %s）",
                    detectionDrop * 100, previous.version(), current.version()));
        }
        return new Regression(!reasons.isEmpty(), reasons);
    }

    public void save() {
        Json.writeFile(file, entries);
    }

    public Path file() {
        return file;
    }

    private int indexOf(String version) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).version().equals(version)) {
                return i;
            }
        }
        return -1;
    }

    private List<Entry> load() {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (content.isBlank()) {
                return List.of();
            }
            return List.of(Json.read(content, Entry[].class));
        } catch (IOException ex) {
            throw new UncheckedIOException("读取趋势历史失败: " + file, ex);
        }
    }
}
