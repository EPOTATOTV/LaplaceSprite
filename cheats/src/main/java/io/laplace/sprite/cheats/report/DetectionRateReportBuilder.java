package io.laplace.sprite.cheats.report;

import io.laplace.sprite.cheats.CheatCatalog;
import io.laplace.sprite.cheats.SampleSpec;
import io.laplace.sprite.core.model.CheatType;
import io.laplace.sprite.core.model.DetectionBreakdown;
import io.laplace.sprite.core.model.FlagEvent;
import io.laplace.sprite.core.model.SampleTierName;
import io.laplace.sprite.core.model.TestRun;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 由一次运行与样本库构建 {@link DetectionRateReport}。
 *
 * <p><b>分子是「检出样本数」而不是 flag 数</b>：一条作弊样本可能同时命中多个检测项，
 * 用 flag 数当分子会算出超过 100% 的检出率。这里按 {@code playerId} 去重——一次注入对应
 * 一个模拟玩家、一个 flag 只要属于该玩家即视为该样本被检出。</p>
 *
 * <p><b>分母只算本次实际注入的档位</b>：用 {@code breakdown.samplesByTier()} 判定哪些档位真的跑过。
 * 只跑 {@code --tier DEFAULT} 时，MIN / MAX / HIDDEN 的分母为 0，不会伪造出 0% 检出。</p>
 *
 * <p><b>按目标</b>：桶键取 {@link TestRun#target()}。一次运行只针对一个被测目标，所以当前只有
 * 一行；保留列表结构是为了多目标汇总时直接追加。<b>按样本类型</b>：作弊子类按 {@link CheatType}
 * 逐型分桶，通用异常样本按各自 {@code sampleType} 分桶。</p>
 *
 * <p><b>非作弊样本不参与检出率</b>：它们描述功能鲁棒性，{@code expectedDetected} 恒为 false。
 * 当前 Runner 只对携带 TestSpec 的作弊会话累计 {@code samplesByTier}，因此通用异常的样本数一律按
 * 「本次未注入」计为 0，并出现在未覆盖项清单里；即使将来注入，它们命中与否也只算功能性失败，
 * 不能写进检出率分子。</p>
 */
public final class DetectionRateReportBuilder {

    public DetectionRateReport build(TestRun run, CheatCatalog catalog) {
        DetectionBreakdown breakdown = run.breakdown();
        Map<SampleTierName, Set<String>> detectedPlayersByTier = detectedPlayersByTier(run);
        Map<CheatType, Map<SampleTierName, Set<String>>> detectedByTypeTier =
                detectedPlayersByTypeTier(run);
        Set<SampleTierName> injectedTiers = injectedTiers(breakdown);

        List<TierRate> byTier = new ArrayList<>();
        int totalSamples = 0;
        int totalHits = 0;
        for (SampleTierName tier : SampleTierName.values()) {
            int samples = breakdown.samplesByTier().getOrDefault(tier, 0);
            int hits = detectedPlayersByTier.getOrDefault(tier, Set.of()).size();
            byTier.add(new TierRate(tier, hits, samples));
            totalSamples += samples;
            totalHits += hits;
        }

        String target = run.target() == null ? "unknown" : run.target();
        List<RateRow> byTarget = List.of(new RateRow(target, List.copyOf(byTier), totalHits, totalSamples));
        List<SampleSpec> specs = catalog.allSamples();
        List<RateRow> bySampleType = bySampleType(specs, detectedByTypeTier, injectedTiers);
        List<UncoveredEntry> uncovered = uncovered(specs, injectedTiers);
        return new DetectionRateReport(run.id(), totalSamples, totalHits, byTarget, bySampleType, byTier,
                uncovered);
    }

    private static List<RateRow> bySampleType(List<SampleSpec> specs,
                                              Map<CheatType, Map<SampleTierName, Set<String>>> detected,
                                              Set<SampleTierName> injectedTiers) {
        Map<String, Integer> specCounts = new LinkedHashMap<>();
        Map<String, CheatType> cheatTypes = new LinkedHashMap<>();
        for (SampleSpec spec : specs) {
            specCounts.merge(spec.sampleType(), 1, Integer::sum);
            if (spec.isCheat()) {
                cheatTypes.putIfAbsent(spec.sampleType(), spec.cheatType());
            }
        }
        List<RateRow> rows = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : specCounts.entrySet()) {
            CheatType cheatType = cheatTypes.get(entry.getKey());
            List<TierRate> tiers = new ArrayList<>();
            int totalHits = 0;
            int totalSamples = 0;
            for (SampleTierName tier : SampleTierName.values()) {
                // 通用异常尚未进入 Runner 的样本计数，注入档位只对作弊子类成立。
                int samples = cheatType != null && injectedTiers.contains(tier) ? entry.getValue() : 0;
                int hits = cheatType == null ? 0
                        : detected.getOrDefault(cheatType, Map.of()).getOrDefault(tier, Set.of()).size();
                tiers.add(new TierRate(tier, hits, samples));
                totalHits += hits;
                totalSamples += samples;
            }
            rows.add(new RateRow(entry.getKey(), tiers, totalHits, totalSamples));
        }
        return List.copyOf(rows);
    }

    private static List<UncoveredEntry> uncovered(List<SampleSpec> specs, Set<SampleTierName> injectedTiers) {
        List<UncoveredEntry> result = new ArrayList<>();
        for (SampleSpec spec : specs) {
            // samplesByTier 只累计作弊会话；通用异常样本即使某个档位有作弊样本跑过，
            // 也不代表它被注入，因此整条样本都算未覆盖。
            List<SampleTierName> missing = spec.isCheat()
                    ? spec.declaredTiers().stream().filter(tier -> !injectedTiers.contains(tier)).toList()
                    : spec.declaredTiers();
            if (!missing.isEmpty()) {
                result.add(new UncoveredEntry(spec.category(), spec.id(), spec.sampleType(), missing));
            }
        }
        return List.copyOf(result);
    }

    /** 本次真的注入过的档位：以运行时的样本计数为准，而不是样本库声明。 */
    static Set<SampleTierName> injectedTiers(DetectionBreakdown breakdown) {
        Set<SampleTierName> tiers = EnumSet.noneOf(SampleTierName.class);
        breakdown.samplesByTier().forEach((tier, count) -> {
            if (count != null && count > 0) {
                tiers.add(tier);
            }
        });
        return tiers;
    }

    /** 按档位归集的已检出玩家：一个玩家属于一条样本，去重后即检出样本数。 */
    static Map<SampleTierName, Set<String>> detectedPlayersByTier(TestRun run) {
        Map<SampleTierName, Set<String>> result = new EnumMap<>(SampleTierName.class);
        for (FlagEvent flag : run.flags()) {
            if (isCheatDetection(flag) && flag.tier() != null) {
                result.computeIfAbsent(flag.tier(), ignored -> new LinkedHashSet<>())
                        .add(flag.playerId());
            }
        }
        return result;
    }

    /** 按 (作弊类型, 档位) 归集的已检出玩家。 */
    static Map<CheatType, Map<SampleTierName, Set<String>>> detectedPlayersByTypeTier(TestRun run) {
        Map<CheatType, Map<SampleTierName, Set<String>>> result = new EnumMap<>(CheatType.class);
        for (FlagEvent flag : run.flags()) {
            if (!isCheatDetection(flag) || flag.cheatType() == null || flag.tier() == null) {
                continue;
            }
            result.computeIfAbsent(flag.cheatType(), ignored -> new EnumMap<>(SampleTierName.class))
                    .computeIfAbsent(flag.tier(), ignored -> new LinkedHashSet<>())
                    .add(flag.playerId());
        }
        return result;
    }

    /** 真检出：带作弊类型的 TP flag，排除误报与被豁免的合规触发。 */
    private static boolean isCheatDetection(FlagEvent flag) {
        return flag.cheatType() != null && !flag.falsePositive() && !flag.isExempted();
    }
}
