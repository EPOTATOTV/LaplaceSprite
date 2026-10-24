package io.laplace.sprite.cheats.report;

import io.laplace.sprite.cheats.SampleCategory;
import io.laplace.sprite.core.model.CheatType;
import io.laplace.sprite.core.model.DetectionBreakdown;
import io.laplace.sprite.core.model.FlagEvent;
import io.laplace.sprite.core.model.MetricSet;
import io.laplace.sprite.core.model.RunStatus;
import io.laplace.sprite.core.model.SampleTierName;
import io.laplace.sprite.core.model.TestRun;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 断言结果三视图（按目标 / 按样本类型 / 按档位）与未覆盖项清单。 */
class DetectionRateReportTest {

    private static final double DELTA = 1e-9;
    private static final int CHEAT_SPECS = 44;
    private static final int NON_CHEAT_SPECS = 18;

    @Test
    void aggregatesThreeViewsFromBreakdown() {
        DetectionRateReport report = new DetectionRateReportBuilder().build(
                ReportFixtures.run("run-rate", ReportFixtures.detected(CheatType.SPEED, 1, 0, 1, 1),
                        ReportFixtures.samplesPerTier(CHEAT_SPECS)),
                ReportFixtures.CATALOG);

        assertEquals("run-rate", report.runId());
        assertEquals(176, report.totalSamples());
        assertEquals(3, report.totalHits());

        // 按目标：一次运行只有一个目标桶
        assertEquals(1, report.byTarget().size());
        RateRow target = report.byTarget().get(0);
        assertEquals("detection-engine", target.label());
        assertEquals(3, target.hits());
        assertEquals(176, target.samples());

        // 按样本类型：SPEED 在样本库里有 5 条样本，故每档样本数 5
        RateRow speed = ReportFixtures.rowOf(report.bySampleType(), "SPEED");
        assertEquals(1, ReportFixtures.tierOf(speed, SampleTierName.MIN).hits());
        assertEquals(5, ReportFixtures.tierOf(speed, SampleTierName.MIN).samples());
        assertEquals(0, ReportFixtures.tierOf(speed, SampleTierName.DEFAULT).hits());
        assertEquals(3, speed.hits());
        assertEquals(20, speed.samples());
        assertEquals(3.0 / 20.0, speed.rate(), DELTA);

        // 按档位
        assertEquals(4, report.byTier().size());
        TierRate min = report.byTier().get(0);
        assertEquals(SampleTierName.MIN, min.tier());
        assertEquals(1, min.hits());
        assertEquals(CHEAT_SPECS, min.samples());
        assertEquals(0, report.byTier().get(1).hits());
        assertEquals(CHEAT_SPECS, report.byTier().get(1).samples());
    }

    @Test
    void oneSampleProducingManyFlagsStillCountsOnce() {
        // 一条样本同时命中两个检测项：flag 两条，但检出样本只有一条，检出率不能超过 100%
        Map<String, Map<SampleTierName, Integer>> hits = new LinkedHashMap<>();
        hits.put("sim.speed", tierCounts(1, 0, 0, 0));
        hits.put("sim.reach", tierCounts(1, 0, 0, 0));
        EnumMap<SampleTierName, Integer> samples = new EnumMap<>(SampleTierName.class);
        samples.put(SampleTierName.MIN, 1);

        List<FlagEvent> flags = List.of(
                FlagEvent.truePositive("run-multi", "p1", "sim.speed", CheatType.SPEED,
                        SampleTierName.MIN, 0.9, 1, "trajectory", null),
                FlagEvent.truePositive("run-multi", "p1", "sim.reach", CheatType.SPEED,
                        SampleTierName.MIN, 0.9, 2, "trajectory", null));
        TestRun run = new TestRun("run-multi", "detection-engine", "cheat", null, MetricSet.empty(),
                new DetectionBreakdown(hits, samples, Map.of(), Map.of()), List.of(), flags,
                RunStatus.PASSED, 1L, Instant.EPOCH, 0L);

        DetectionRateReport report = new DetectionRateReportBuilder().build(run, ReportFixtures.CATALOG);

        assertEquals(1, report.totalSamples());
        assertEquals(1, report.totalHits());
        assertEquals(1.0, report.overallRate(), DELTA);
    }

    @Test
    void tiersThatWereNotInjectedDoNotProduceZeroPercentBuckets() {
        // 只跑 DEFAULT：MIN / MAX / HIDDEN 不该被算成 0% 检出
        EnumMap<SampleTierName, Integer> samples = new EnumMap<>(SampleTierName.class);
        samples.put(SampleTierName.DEFAULT, CHEAT_SPECS);
        Map<CheatType, Map<SampleTierName, Integer>> detected = new EnumMap<>(CheatType.class);
        Map<SampleTierName, Integer> byTier = new EnumMap<>(SampleTierName.class);
        byTier.put(SampleTierName.DEFAULT, 4);
        detected.put(CheatType.SPEED, byTier);

        DetectionRateReport report = new DetectionRateReportBuilder().build(
                ReportFixtures.run("run-default-only", detected, samples), ReportFixtures.CATALOG);

        assertEquals(CHEAT_SPECS, report.totalSamples());
        assertEquals(4, report.totalHits());
        assertFalse(report.byTier().get(0).hasSamples());
        assertTrue(report.byTier().get(1).hasSamples());
        assertEquals(4.0 / CHEAT_SPECS, report.overallRate(), DELTA);
    }

    @Test
    void nonCheatSampleTypesCarryNoDetectionSamples() {
        // 通用异常样本描述功能鲁棒性，不进入检出率分母；在作弊运行里显示为「无样本」
        DetectionRateReport report = new DetectionRateReportBuilder().build(
                ReportFixtures.run("run-non-cheat", ReportFixtures.fullyDetected(),
                        ReportFixtures.samplesPerTier(CHEAT_SPECS)),
                ReportFixtures.CATALOG);

        RateRow registry = ReportFixtures.rowOf(report.bySampleType(), "DUPLICATE_REGISTRY_ENTRY");
        assertFalse(registry.hasSamples());
        assertEquals("无样本", registry.counts());
        assertEquals(0, registry.hits());

        RateRow cheat = ReportFixtures.rowOf(report.bySampleType(), "SPEED");
        assertEquals(20, cheat.samples());
        assertTrue(cheat.hits() > 0);
    }

    @Test
    void zeroSampleBucketsAreNotNaN() {
        Map<String, Map<SampleTierName, Integer>> hits = new LinkedHashMap<>();
        hits.put("sim.made-up", tierCounts(0, 0, 0, 0));
        EnumMap<SampleTierName, Integer> samplesByTier = new EnumMap<>(SampleTierName.class);
        samplesByTier.put(SampleTierName.DEFAULT, CHEAT_SPECS);
        TestRun run = new TestRun("run-partial", "detection-engine", "cheat", null, MetricSet.empty(),
                new DetectionBreakdown(hits, samplesByTier, Map.of(), Map.of()), List.of(), List.of(),
                RunStatus.PASSED, 1L, Instant.EPOCH, 0L);

        DetectionRateReport report = new DetectionRateReportBuilder().build(run, ReportFixtures.CATALOG);

        TierRate min = report.byTier().get(0);
        assertFalse(min.hasSamples());
        assertFalse(Double.isNaN(min.rate()));
        assertEquals(0.0, min.rate(), DELTA);
        assertEquals("无样本", min.text());

        // 样本库里没有命中的通用异常类型：没有任何样本支撑，不能显示成 0% 检出
        RateRow unknown = ReportFixtures.rowOf(report.bySampleType(), "INVALID_INPUT_NAN_COORDINATES");
        assertFalse(unknown.hasSamples());
        assertEquals(0.0, unknown.rate(), DELTA);
        assertEquals("无样本", unknown.counts());

        assertTrue(report.toMarkdown().contains("无样本"));
    }

    @Test
    void realCatalogProducesAllThreeViews() {
        DetectionRateReport report = new DetectionRateReportBuilder().build(
                ReportFixtures.run("run-full", ReportFixtures.fullyDetected(),
                        ReportFixtures.samplesPerTier(CHEAT_SPECS)),
                ReportFixtures.CATALOG);

        assertEquals(15, report.bySampleType().stream()
                .filter(row -> row.hasSamples()).count());
        assertEquals(15 + NON_CHEAT_SPECS, report.bySampleType().size());
        assertEquals(1, report.byTarget().size());
        assertEquals(4, report.byTier().size());
        assertEquals(176, report.totalSamples());
        assertEquals(176, report.totalHits());
        assertEquals(1.0, report.overallRate(), DELTA);

        String markdown = report.toMarkdown();
        assertTrue(markdown.contains("### 按目标"));
        assertTrue(markdown.contains("### 按样本类型"));
        assertTrue(markdown.contains("### 按档位"));
        assertTrue(markdown.contains("### 未覆盖项清单"));
        assertTrue(markdown.contains("SPEED"));
        assertTrue(markdown.contains("detection-engine"));
        assertTrue(markdown.contains("#### ANOMALY"));
    }

    @Test
    void uncoveredListsMissingTiersGroupedByCategory() {
        // 只跑 DEFAULT：作弊样本各缺三档，通用异常样本整类未注入
        EnumMap<SampleTierName, Integer> samples = new EnumMap<>(SampleTierName.class);
        samples.put(SampleTierName.DEFAULT, CHEAT_SPECS);
        DetectionRateReport report = new DetectionRateReportBuilder().build(
                ReportFixtures.run("run-uncovered", ReportFixtures.fullyDetected(), samples),
                ReportFixtures.CATALOG);

        assertEquals(CHEAT_SPECS + NON_CHEAT_SPECS, report.uncovered().size());
        assertEquals(CHEAT_SPECS, report.uncovered().stream()
                .filter(entry -> entry.category() == SampleCategory.CHEAT).count());
        assertEquals(NON_CHEAT_SPECS, report.uncovered().stream()
                .filter(entry -> entry.category() != SampleCategory.CHEAT).count());

        UncoveredEntry speed = report.uncovered().stream()
                .filter(entry -> entry.sampleId().equals("speed.wurst.default")).findFirst().orElseThrow();
        assertEquals(List.of(SampleTierName.MIN, SampleTierName.MAX, SampleTierName.HIDDEN),
                speed.missingTiers());

        UncoveredEntry anomaly = report.uncovered().stream()
                .filter(entry -> entry.sampleId().equals("anomaly.nan-coordinates.default")).findFirst()
                .orElseThrow();
        assertEquals(SampleCategory.ANOMALY, anomaly.category());
        assertEquals(4, anomaly.missingTiers().size());

        String markdown = report.toMarkdown();
        assertTrue(markdown.contains("#### ANOMALY"));
        assertTrue(markdown.contains("anomaly.nan-coordinates.default"));
    }

    @Test
    void fullTierRunOnlyLeavesNonCheatCoverageGaps() {
        DetectionRateReport report = new DetectionRateReportBuilder().build(
                ReportFixtures.run("run-full-tier", ReportFixtures.fullyDetected(),
                        ReportFixtures.samplesPerTier(CHEAT_SPECS)),
                ReportFixtures.CATALOG);

        assertEquals(NON_CHEAT_SPECS, report.uncovered().size());
        assertTrue(report.uncovered().stream().noneMatch(entry -> entry.category() == SampleCategory.CHEAT));
    }

    @Test
    void markdownCarriesHeadersAndNumbers() {
        DetectionRateReport report = new DetectionRateReportBuilder().build(
                ReportFixtures.run("run-md", ReportFixtures.detected(CheatType.SPEED, 1, 0, 1, 1),
                        ReportFixtures.samplesPerTier(CHEAT_SPECS)),
                ReportFixtures.CATALOG);

        String markdown = report.toMarkdown();
        assertTrue(markdown.contains("## 检出率报告（运行 run-md）"));
        assertTrue(markdown.contains("总样本 176，命中 3"));
        assertTrue(markdown.contains("### 按目标"));
        assertTrue(markdown.contains("### 按样本类型"));
        assertTrue(markdown.contains("### 按档位"));
        assertTrue(markdown.contains("3/20"));
        assertTrue(markdown.contains("15.0%"));
    }

    private static EnumMap<SampleTierName, Integer> tierCounts(int min, int def, int max, int hidden) {
        EnumMap<SampleTierName, Integer> counts = new EnumMap<>(SampleTierName.class);
        counts.put(SampleTierName.MIN, min);
        counts.put(SampleTierName.DEFAULT, def);
        counts.put(SampleTierName.MAX, max);
        counts.put(SampleTierName.HIDDEN, hidden);
        return counts;
    }
}
