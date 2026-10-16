package io.laplace.sprite.core.engine;

import io.laplace.sprite.core.model.CheatTierName;
import io.laplace.sprite.core.model.CheatType;
import io.laplace.sprite.core.model.DetectionBreakdown;
import io.laplace.sprite.core.model.MetricHistory;
import io.laplace.sprite.core.model.MetricSet;
import io.laplace.sprite.core.model.TestRun;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 指标聚合（4.5 判定层）：11.2 六张指标卡片 / 11.7 检出率矩阵的数据源。
 */
public final class MetricAggregator {

    public MetricSet aggregate(int legitPlayers, int falsePositives, int cheatSamples,
                               int detectedSamples, int boundarySamples, int boundaryPassed,
                               boolean concurrencyOk, double coverage) {
        return new MetricSet(legitPlayers, falsePositives, cheatSamples, detectedSamples,
                boundarySamples, boundaryPassed, concurrencyOk, coverage);
    }

    /** 多次运行的合计指标（夜间基准 / 全量基准）。 */
    public static MetricSet sum(List<TestRun> runs) {
        int legit = 0;
        int fp = 0;
        int cheat = 0;
        int detected = 0;
        int boundary = 0;
        int boundaryPassed = 0;
        boolean concurrencyOk = true;
        double coverage = 0.0;
        for (TestRun run : runs) {
            MetricSet m = run.metrics();
            legit += m.legitPlayers();
            fp += m.falsePositives();
            cheat += m.cheatSamples();
            detected += m.detectedSamples();
            boundary += m.boundarySamples();
            boundaryPassed += m.boundaryPassed();
            concurrencyOk &= m.concurrencyOk();
            coverage = Math.max(coverage, m.coverage());
        }
        return new MetricSet(legit, fp, cheat, detected, boundary, boundaryPassed, concurrencyOk, coverage);
    }

    /** 按检测项 × 档位的检出率（分母为该档位注入样本数）。 */
    public static Map<String, Map<CheatTierName, Double>> detectionRateByCheck(List<TestRun> runs) {
        Map<String, Map<CheatTierName, Double>> out = new LinkedHashMap<>();
        Map<CheatTierName, Integer> samples = samplesByTier(runs);
        for (String check : checkNames(runs)) {
            Map<CheatTierName, Integer> hits = new EnumMap<>(CheatTierName.class);
            for (TestRun run : runs) {
                Map<CheatTierName, Integer> row = run.breakdown().hitsByCheckTier().get(check);
                if (row != null) {
                    row.forEach((tier, count) -> hits.merge(tier, count, Integer::sum));
                }
            }
            Map<CheatTierName, Double> rates = new EnumMap<>(CheatTierName.class);
            for (CheatTierName tier : CheatTierName.values()) {
                int denominator = samples.getOrDefault(tier, 0);
                if (denominator > 0) {
                    rates.put(tier, (double) hits.getOrDefault(tier, 0) / denominator);
                }
            }
            out.put(check, rates);
        }
        return out;
    }

    /** 按档位的整体检出率。 */
    public static Map<CheatTierName, Double> detectionRateByTier(List<TestRun> runs) {
        Map<CheatTierName, Integer> samples = samplesByTier(runs);
        Map<CheatTierName, Integer> hits = new EnumMap<>(CheatTierName.class);
        for (TestRun run : runs) {
            run.breakdown().hitsByCheckTier().values()
                    .forEach(row -> row.forEach((tier, count) -> hits.merge(tier, count, Integer::sum)));
        }
        Map<CheatTierName, Double> rates = new EnumMap<>(CheatTierName.class);
        for (CheatTierName tier : CheatTierName.values()) {
            int denominator = samples.getOrDefault(tier, 0);
            if (denominator > 0) {
                rates.put(tier, (double) hits.getOrDefault(tier, 0) / denominator);
            }
        }
        return rates;
    }

    /** 按作弊类型（客户端模块）的检出率。 */
    public static Map<CheatType, Double> detectionRateByCheatType(List<TestRun> runs) {
        Map<CheatType, Integer> samples = new EnumMap<>(CheatType.class);
        Map<CheatType, Integer> hits = new EnumMap<>(CheatType.class);
        for (TestRun run : runs) {
            DetectionBreakdown b = run.breakdown();
            b.samplesByCheatType().forEach((type, c) -> samples.merge(type, c, Integer::sum));
            b.hitsByCheatType().forEach((type, c) -> hits.merge(type, c, Integer::sum));
        }
        Map<CheatType, Double> rates = new EnumMap<>(CheatType.class);
        samples.forEach((type, denominator) -> {
            if (denominator > 0) {
                rates.put(type, (double) hits.getOrDefault(type, 0) / denominator);
            }
        });
        return rates;
    }

    public static Map<CheatTierName, Integer> samplesByTier(List<TestRun> runs) {
        Map<CheatTierName, Integer> samples = new EnumMap<>(CheatTierName.class);
        for (TestRun run : runs) {
            run.breakdown().samplesByTier().forEach((tier, c) -> samples.merge(tier, c, Integer::sum));
        }
        return samples;
    }

    public static Set<String> checkNames(List<TestRun> runs) {
        Set<String> names = new LinkedHashSet<>();
        runs.forEach(r -> names.addAll(r.breakdown().hitsByCheckTier().keySet()));
        runs.forEach(r -> r.detections().forEach(f -> names.add(f.checkName())));
        return names;
    }

    /** 指标随版本演化（11.2 趋势图 / 12.4 基线入库）；越界自动标记。 */
    public static MetricHistory history(String metric, double threshold, List<TestRun> runs) {
        var points = new java.util.ArrayList<MetricHistory.Point>();
        int index = 0;
        for (TestRun run : runs) {
            double value = switch (metric) {
                case "false_positive_rate" -> run.metrics().falsePositiveRate();
                case "detection_rate" -> run.metrics().detectionRate();
                case "coverage" -> run.metrics().coverage();
                default -> 0.0;
            };
            boolean breached = metric.startsWith("false_positive")
                    ? value > threshold
                    : value < threshold;
            points.add(new MetricHistory.Point("run-" + (++index), run.startedAt(), value, breached));
        }
        return new MetricHistory(metric, threshold, points);
    }
}
