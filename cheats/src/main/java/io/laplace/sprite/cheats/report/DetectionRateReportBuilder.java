package io.laplace.sprite.cheats.report;

import io.laplace.sprite.cheats.CheatCatalog;
import io.laplace.sprite.cheats.threshold.DetectionThresholdAnchor;
import io.laplace.sprite.core.model.CheatSpec;
import io.laplace.sprite.core.model.CheatTierName;
import io.laplace.sprite.core.model.CheatType;
import io.laplace.sprite.core.model.DetectionBreakdown;
import io.laplace.sprite.core.model.FlagEvent;
import io.laplace.sprite.core.model.TestRun;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

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
 * <p><b>按客户端的归桶规则</b>：同一 {@link CheatType} 可能同时挂在多个 {@code source} 标签下
 * （如 SPEED 出现在 Wurst / LiquidBounce / Meteor / Lambda）。这里按样本库把该客户端的样本数记为
 * 「该客户端名下所有 Spec 在注入档位上的样本数之和」，检出数取这些类型检出样本的并集
 * （同一玩家只属于一个类型，因此并集等于直接相加）。这是口径选择：宁可对多客户端共有的类型
 * 重复计数，也不虚构样本级的单归属。</p>
 */
public final class DetectionRateReportBuilder {

    public DetectionRateReport build(TestRun run, CheatCatalog catalog) {
        DetectionBreakdown breakdown = run.breakdown();
        Map<CheatTierName, Set<String>> detectedPlayersByTier = detectedPlayersByTier(run);
        Map<CheatType, Map<CheatTierName, Set<String>>> detectedByTypeTier =
                detectedPlayersByTypeTier(run);
        Set<CheatTierName> injectedTiers = injectedTiers(breakdown);

        Map<CheatType, Integer> specsPerType = new EnumMap<>(CheatType.class);
        Map<String, Set<CheatType>> checkTypes = new LinkedHashMap<>();
        Map<String, Set<CheatType>> clientTypes = new LinkedHashMap<>();
        Map<String, Integer> specsPerClient = new LinkedHashMap<>();

        for (CheatSpec spec : catalog.loadDefaults()) {
            String check = DetectionThresholdAnchor.of(spec.type()).checkName();
            specsPerType.merge(spec.type(), 1, Integer::sum);
            checkTypes.computeIfAbsent(check, ignored -> EnumSet.noneOf(CheatType.class)).add(spec.type());
            clientTypes.computeIfAbsent(spec.source(), ignored -> EnumSet.noneOf(CheatType.class))
                    .add(spec.type());
            specsPerClient.merge(spec.source(), 1, Integer::sum);
        }

        List<RateRow> byCheck = new ArrayList<>();
        for (String check : new TreeSet<>(checkTypes.keySet())) {
            Set<CheatType> types = checkTypes.get(check);
            int specs = types.stream().mapToInt(type -> specsPerType.getOrDefault(type, 0)).sum();
            byCheck.add(row(check, types, specs, detectedByTypeTier, injectedTiers));
        }
        // 引擎报了但样本库里没有对应类型的检测项：保留一行，样本数为 0 并标「无样本」，
        // 不要因为没样本就把它从视图里抹掉。
        for (String check : new TreeSet<>(breakdown.hitsByCheckTier().keySet())) {
            if (!checkTypes.containsKey(check)) {
                byCheck.add(row(check, Set.of(), 0, detectedByTypeTier, injectedTiers));
            }
        }

        List<RateRow> byClient = new ArrayList<>();
        for (String client : new TreeSet<>(clientTypes.keySet())) {
            byClient.add(row(client, clientTypes.get(client),
                    specsPerClient.getOrDefault(client, 0), detectedByTypeTier, injectedTiers));
        }

        List<TierRate> byTier = new ArrayList<>();
        int totalSamples = 0;
        int totalHits = 0;
        for (CheatTierName tier : CheatTierName.values()) {
            int samples = breakdown.samplesByTier().getOrDefault(tier, 0);
            int hits = detectedPlayersByTier.getOrDefault(tier, Set.of()).size();
            byTier.add(new TierRate(tier, hits, samples));
            totalSamples += samples;
            totalHits += hits;
        }
        return new DetectionRateReport(run.id(), totalSamples, totalHits, byCheck, byClient, byTier);
    }

    /** 某组类型在本次注入档位上的样本级检出行。 */
    private static RateRow row(String label, Set<CheatType> types, int specs,
                               Map<CheatType, Map<CheatTierName, Set<String>>> detected,
                               Set<CheatTierName> injectedTiers) {
        List<TierRate> tiers = new ArrayList<>();
        int totalHits = 0;
        int totalSamples = 0;
        for (CheatTierName tier : CheatTierName.values()) {
            Set<String> players = new LinkedHashSet<>();
            for (CheatType type : types) {
                players.addAll(detected.getOrDefault(type, Map.of()).getOrDefault(tier, Set.of()));
            }
            int samples = injectedTiers.contains(tier) ? specs : 0;
            int hits = players.size();
            tiers.add(new TierRate(tier, hits, samples));
            totalHits += hits;
            totalSamples += samples;
        }
        return new RateRow(label, tiers, totalHits, totalSamples);
    }

    /** 本次真的注入过的档位：以运行时的样本计数为准，而不是样本库声明。 */
    static Set<CheatTierName> injectedTiers(DetectionBreakdown breakdown) {
        Set<CheatTierName> tiers = EnumSet.noneOf(CheatTierName.class);
        breakdown.samplesByTier().forEach((tier, count) -> {
            if (count != null && count > 0) {
                tiers.add(tier);
            }
        });
        return tiers;
    }

    /** 按档位归集的已检出玩家：一个玩家属于一条样本，去重后即检出样本数。 */
    static Map<CheatTierName, Set<String>> detectedPlayersByTier(TestRun run) {
        Map<CheatTierName, Set<String>> result = new EnumMap<>(CheatTierName.class);
        for (FlagEvent flag : run.flags()) {
            if (isCheatDetection(flag) && flag.tier() != null) {
                result.computeIfAbsent(flag.tier(), ignored -> new LinkedHashSet<>())
                        .add(flag.playerId());
            }
        }
        return result;
    }

    /** 按 (作弊类型, 档位) 归集的已检出玩家。 */
    static Map<CheatType, Map<CheatTierName, Set<String>>> detectedPlayersByTypeTier(TestRun run) {
        Map<CheatType, Map<CheatTierName, Set<String>>> result = new EnumMap<>(CheatType.class);
        for (FlagEvent flag : run.flags()) {
            if (!isCheatDetection(flag) || flag.cheatType() == null || flag.tier() == null) {
                continue;
            }
            result.computeIfAbsent(flag.cheatType(), ignored -> new EnumMap<>(CheatTierName.class))
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
