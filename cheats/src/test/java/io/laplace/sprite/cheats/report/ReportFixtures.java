package io.laplace.sprite.cheats.report;

import io.laplace.sprite.cheats.CheatCatalog;
import io.laplace.sprite.cheats.threshold.DetectionThresholdAnchor;
import io.laplace.sprite.core.model.CheatSpec;
import io.laplace.sprite.core.model.CheatTierName;
import io.laplace.sprite.core.model.CheatType;
import io.laplace.sprite.core.model.DetectionBreakdown;
import io.laplace.sprite.core.model.FlagEvent;
import io.laplace.sprite.core.model.MetricSet;
import io.laplace.sprite.core.model.RunStatus;
import io.laplace.sprite.core.model.TestRun;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 报告测试用的样本库与运行夹具。
 *
 * <p>夹具按 {@code Runner} 的真实产出形状构造：{@code samplesByTier} 记录本次实际注入的档位，
 * {@code flags} 记录检出明细。检出样本数由 flag 的 {@code playerId} 去重得到。</p>
 */
final class ReportFixtures {

    static final CheatCatalog CATALOG = new CheatCatalog();
    static final CheatTierName[] TIERS = CheatTierName.values();

    private ReportFixtures() {
    }

    /** 每种作弊类型的样本条数（来自内置样本库）。 */
    static Map<CheatType, Integer> specsPerType() {
        Map<CheatType, Integer> counts = new EnumMap<>(CheatType.class);
        for (CheatSpec spec : CATALOG.loadDefaults()) {
            counts.merge(spec.type(), 1, Integer::sum);
        }
        return counts;
    }

    /** 四个档位各注入 {@code perTier} 条样本。 */
    static EnumMap<CheatTierName, Integer> samplesPerTier(int perTier) {
        EnumMap<CheatTierName, Integer> samples = new EnumMap<>(CheatTierName.class);
        for (CheatTierName tier : TIERS) {
            samples.put(tier, perTier);
        }
        return samples;
    }

    /** 单个类型在四档的检出样本数。 */
    static Map<CheatType, Map<CheatTierName, Integer>> detected(CheatType type,
                                                                int min, int def, int max, int hidden) {
        Map<CheatTierName, Integer> byTier = new EnumMap<>(CheatTierName.class);
        byTier.put(CheatTierName.MIN, min);
        byTier.put(CheatTierName.DEFAULT, def);
        byTier.put(CheatTierName.MAX, max);
        byTier.put(CheatTierName.HIDDEN, hidden);
        Map<CheatType, Map<CheatTierName, Integer>> result = new EnumMap<>(CheatType.class);
        result.put(type, byTier);
        return result;
    }

    /** 全量检出：每个类型的每条样本在每档都检出。 */
    static Map<CheatType, Map<CheatTierName, Integer>> fullyDetected() {
        Map<CheatType, Integer> specs = specsPerType();
        Map<CheatType, Map<CheatTierName, Integer>> result = new EnumMap<>(CheatType.class);
        for (CheatType type : CheatType.values()) {
            Map<CheatTierName, Integer> byTier = new EnumMap<>(CheatTierName.class);
            for (CheatTierName tier : TIERS) {
                byTier.put(tier, specs.getOrDefault(type, 0));
            }
            result.put(type, byTier);
        }
        return result;
    }

    /**
     * 由「每类型每档的检出样本数」构造一次运行：合成等量的 TP flag（playerId 唯一），
     * 并同步填好 breakdown，形状与 {@code Runner} 的产出一致。
     */
    static TestRun run(String id, Map<CheatType, Map<CheatTierName, Integer>> detectedByType,
                       Map<CheatTierName, Integer> samplesByTier) {
        Map<String, Map<CheatTierName, Integer>> hitsByCheckTier = new LinkedHashMap<>();
        Map<CheatTierName, Integer> samplesByTierCopy = new EnumMap<>(CheatTierName.class);
        samplesByTier.forEach(samplesByTierCopy::put);
        Map<CheatType, Integer> hitsByType = new EnumMap<>(CheatType.class);
        Map<CheatType, Integer> samplesByType = new EnumMap<>(CheatType.class);
        List<FlagEvent> flags = new ArrayList<>();
        long tick = 0;

        for (Map.Entry<CheatType, Map<CheatTierName, Integer>> typeEntry : detectedByType.entrySet()) {
            CheatType type = typeEntry.getKey();
            String check = DetectionThresholdAnchor.of(type).checkName();
            EnumMap<CheatTierName, Integer> byTier = new EnumMap<>(CheatTierName.class);
            for (Map.Entry<CheatTierName, Integer> tierEntry : typeEntry.getValue().entrySet()) {
                int count = tierEntry.getValue();
                if (count <= 0) {
                    continue;
                }
                byTier.put(tierEntry.getKey(), count);
                hitsByType.merge(type, count, Integer::sum);
                for (int i = 0; i < count; i++) {
                    flags.add(FlagEvent.truePositive(id, type.name() + "-" + tierEntry.getKey() + "-" + i,
                            check, type, tierEntry.getKey(), 0.9, ++tick, "trajectory", null));
                }
            }
            if (!byTier.isEmpty()) {
                hitsByCheckTier.put(check, byTier);
            }
        }
        samplesByTier.forEach((tier, count) -> specsPerType()
                .forEach((type, specs) -> samplesByType.merge(type, count > 0 ? specs : 0, Integer::sum)));

        DetectionBreakdown breakdown = new DetectionBreakdown(hitsByCheckTier, samplesByTierCopy,
                hitsByType, samplesByType);
        return new TestRun(id, "detection-engine", "cheat", null, MetricSet.empty(), breakdown,
                List.of(), flags, RunStatus.PASSED, 1L, Instant.EPOCH, 0L);
    }

    static TierRate tierOf(RateRow row, CheatTierName tier) {
        return row.tiers().stream().filter(candidate -> candidate.tier() == tier).findFirst().orElseThrow();
    }

    static RateRow rowOf(List<RateRow> rows, String label) {
        return rows.stream().filter(row -> row.label().equals(label)).findFirst().orElseThrow();
    }
}
