package io.laplace.sprite.cheats.report;

import io.laplace.sprite.cheats.CheatCatalog;
import io.laplace.sprite.cheats.threshold.DetectionThresholdAnchor;
import io.laplace.sprite.core.model.CheatSpec;
import io.laplace.sprite.core.model.CheatTier;
import io.laplace.sprite.core.model.CheatTierName;
import io.laplace.sprite.core.model.CheatType;
import io.laplace.sprite.core.model.DetectionBreakdown;
import io.laplace.sprite.core.model.TestRun;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 绕过清单分析（v1.1.0 §4.2「已实现但被绕过的检测项 → 被测引擎改进 backlog」）。
 *
 * <p><b>绕过的定义</b>：某 {@link CheatType} 在某个「本应被检出」的档位上注入了样本，
 * 但该档位下**一条样本都没被检出**，即记一条 {@link BypassEntry}。两个口径要点：</p>
 * <ul>
 *   <li>「检出」按样本计，不按 flag 计：一条样本可能命中多个检测项，用 flag 数当命中会把
 *       被绕过的样本误判成已检出。这里按 {@code playerId} 去重。</li>
 *   <li>只看<b>本次真的注入过</b>的档位（{@code breakdown.samplesByTier()}）：只跑
 *       {@code --tier DEFAULT} 时，MIN / MAX / HIDDEN 不该被报成绕过。</li>
 * </ul>
 *
 * <p>锚点显式标记 {@code undetectable} 的类型不计入绕过清单，而进入
 * {@link BypassList#undetectableTypes()}——那属于「引擎尚未实现该检测项」，与「已实现但被绕过」
 * 是两类不同的 backlog，不能混在一张表里。</p>
 */
public final class BypassAnalyzer {

    private final Predicate<CheatType> undetectable;

    public BypassAnalyzer() {
        this(type -> DetectionThresholdAnchor.of(type).undetectable());
    }

    /** 供测试注入「不可检出」判定；生产默认走锚点的 {@code undetectable} 标记。 */
    BypassAnalyzer(Predicate<CheatType> undetectable) {
        this.undetectable = undetectable;
    }

    public BypassList analyze(TestRun run, CheatCatalog catalog) {
        DetectionBreakdown breakdown = run.breakdown();
        Set<CheatTierName> injected = DetectionRateReportBuilder.injectedTiers(breakdown);
        Map<CheatType, Map<CheatTierName, Set<String>>> detected =
                DetectionRateReportBuilder.detectedPlayersByTypeTier(run);

        Map<CheatType, String> checkOfType = new EnumMap<>(CheatType.class);
        Map<CheatType, Integer> specsPerType = new EnumMap<>(CheatType.class);
        Map<CheatType, EnumSet<CheatTierName>> expectedTiers = new EnumMap<>(CheatType.class);
        Map<CheatType, LinkedHashSet<String>> sourceSets = new EnumMap<>(CheatType.class);

        for (CheatSpec spec : catalog.loadDefaults()) {
            checkOfType.putIfAbsent(spec.type(), DetectionThresholdAnchor.of(spec.type()).checkName());
            specsPerType.merge(spec.type(), 1, Integer::sum);
            sourceSets.computeIfAbsent(spec.type(), ignored -> new LinkedHashSet<>()).add(spec.source());
            EnumSet<CheatTierName> expected =
                    expectedTiers.computeIfAbsent(spec.type(), ignored -> EnumSet.noneOf(CheatTierName.class));
            for (CheatTier tier : spec.tiers()) {
                if (tier.expected() != null && tier.expected().expectedDetected()) {
                    expected.add(tier.tier());
                }
            }
        }

        List<BypassEntry> entries = new ArrayList<>();
        List<CheatType> undetectableTypes = new ArrayList<>();

        for (CheatType type : CheatType.values()) {
            EnumSet<CheatTierName> expected = expectedTiers.get(type);
            if (expected == null) {
                continue;
            }
            if (undetectable.test(type)) {
                undetectableTypes.add(type);
                continue;
            }
            // 本次注入过、且预期应被检出的档位，才是这轮可比对的档位。
            EnumSet<CheatTierName> comparable = EnumSet.noneOf(CheatTierName.class);
            for (CheatTierName tier : expected) {
                if (injected.contains(tier)) {
                    comparable.add(tier);
                }
            }
            if (comparable.isEmpty()) {
                continue;
            }
            Map<CheatTierName, Set<String>> detectedByTier = detected.getOrDefault(type, Map.of());
            boolean neverDetected = comparable.stream()
                    .allMatch(tier -> detectedByTier.getOrDefault(tier, Set.of()).isEmpty());
            int specs = specsPerType.getOrDefault(type, 0);
            String check = checkOfType.get(type);
            List<String> sources = List.copyOf(sourceSets.get(type));

            for (CheatTierName tier : comparable) {
                int detectedCount = detectedByTier.getOrDefault(tier, Set.of()).size();
                if (detectedCount > 0) {
                    continue;
                }
                entries.add(new BypassEntry(type, sources, tier, specs, 0, neverDetected,
                        recommendation(check, neverDetected)));
            }
        }
        return new BypassList(entries, undetectableTypes);
    }

    private static String recommendation(String check, boolean neverDetected) {
        String action = "检查 " + check + " 检测项的阈值是否被绕过";
        return neverDetected
                ? "该档位下 0 检出，且该类型在本次注入的所有预期档位均未命中：" + action
                : "该档位下 0 检出：" + action;
    }
}
