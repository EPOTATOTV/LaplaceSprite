package io.laplace.sprite.cheats.report;

import io.laplace.sprite.core.model.CheatTierName;
import io.laplace.sprite.core.model.CheatType;
import io.laplace.sprite.core.model.DetectionBreakdown;
import io.laplace.sprite.core.model.FlagEvent;
import io.laplace.sprite.core.model.MetricSet;
import io.laplace.sprite.core.model.RunStatus;
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

/** 检出率报告三视图的聚合与 Markdown 输出。 */
class DetectionRateReportTest {

    private static final double DELTA = 1e-9;

    @Test
    void aggregatesThreeViewsFromBreakdown() {
        DetectionRateReport report = new DetectionRateReportBuilder().build(
                ReportFixtures.run("run-rate", ReportFixtures.detected(CheatType.SPEED, 1, 0, 1, 1),
                        ReportFixtures.samplesPerTier(36)),
                ReportFixtures.CATALOG);

        assertEquals("run-rate", report.runId());
        assertEquals(144, report.totalSamples());
        assertEquals(3, report.totalHits());

        // 按检测项：SPEED 在样本库里有 4 条样本，故 sim.speed 每档样本数 4
        assertEquals(15, report.byCheck().size());
        RateRow speed = ReportFixtures.rowOf(report.byCheck(), "sim.speed");
        assertEquals(1, ReportFixtures.tierOf(speed, CheatTierName.MIN).hits());
        assertEquals(4, ReportFixtures.tierOf(speed, CheatTierName.MIN).samples());
        assertEquals(0, ReportFixtures.tierOf(speed, CheatTierName.DEFAULT).hits());
        assertEquals(3, speed.hits());
        assertEquals(16, speed.samples());
        assertEquals(3.0 / 16.0, speed.rate(), DELTA);

        // 按档位
        assertEquals(4, report.byTier().size());
        TierRate min = report.byTier().get(0);
        assertEquals(CheatTierName.MIN, min.tier());
        assertEquals(1, min.hits());
        assertEquals(36, min.samples());
        assertEquals(0, report.byTier().get(1).hits());
        assertEquals(36, report.byTier().get(1).samples());
    }

    @Test
    void oneSampleProducingManyFlagsStillCountsOnce() {
        // 一条样本同时命中两个检测项：flag 两条，但检出样本只有一条，检出率不能超过 100%
        Map<String, Map<CheatTierName, Integer>> hits = new LinkedHashMap<>();
        hits.put("sim.speed", tierCounts(1, 0, 0, 0));
        hits.put("sim.reach", tierCounts(1, 0, 0, 0));
        EnumMap<CheatTierName, Integer> samples = new EnumMap<>(CheatTierName.class);
        samples.put(CheatTierName.MIN, 1);

        List<FlagEvent> flags = List.of(
                FlagEvent.truePositive("run-multi", "p1", "sim.speed", CheatType.SPEED,
                        CheatTierName.MIN, 0.9, 1, "trajectory", null),
                FlagEvent.truePositive("run-multi", "p1", "sim.reach", CheatType.SPEED,
                        CheatTierName.MIN, 0.9, 2, "trajectory", null));
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
        EnumMap<CheatTierName, Integer> samples = new EnumMap<>(CheatTierName.class);
        samples.put(CheatTierName.DEFAULT, 36);
        Map<CheatType, Map<CheatTierName, Integer>> detected = new EnumMap<>(CheatType.class);
        Map<CheatTierName, Integer> byTier = new EnumMap<>(CheatTierName.class);
        byTier.put(CheatTierName.DEFAULT, 4);
        detected.put(CheatType.SPEED, byTier);

        DetectionRateReport report = new DetectionRateReportBuilder().build(
                ReportFixtures.run("run-default-only", detected, samples), ReportFixtures.CATALOG);

        assertEquals(36, report.totalSamples());
        assertEquals(4, report.totalHits());
        assertFalse(report.byTier().get(0).hasSamples());
        assertTrue(report.byTier().get(1).hasSamples());
        assertEquals(4.0 / 36.0, report.overallRate(), DELTA);
    }

    @Test
    void sharedCheatTypeCountsIntoEveryClientBucket() {
        // SPEED 同时挂在 Wurst / LiquidBounce / Meteor / Lambda 名下，检出应计入每个桶
        DetectionRateReport report = new DetectionRateReportBuilder().build(
                ReportFixtures.run("run-client", ReportFixtures.detected(CheatType.SPEED, 1, 1, 1, 1),
                        ReportFixtures.samplesPerTier(36)),
                ReportFixtures.CATALOG);

        assertEquals(6, report.byClient().size());
        RateRow wurst = ReportFixtures.rowOf(report.byClient(), "Wurst");
        RateRow liquidbounce = ReportFixtures.rowOf(report.byClient(), "LiquidBounce");
        RateRow lambda = ReportFixtures.rowOf(report.byClient(), "Lambda");
        assertEquals(4, wurst.hits());
        assertEquals(4, liquidbounce.hits());
        assertEquals(4, lambda.hits());
        assertEquals(36, wurst.samples());
        assertEquals(32, liquidbounce.samples());
        assertEquals(16, lambda.samples());

        // 未涉及其作弊类型的客户端保持 0 命中
        RateRow bleachhack = ReportFixtures.rowOf(report.byClient(), "BleachHack");
        assertEquals(0, bleachhack.hits());
        assertEquals(8, bleachhack.samples());
    }

    @Test
    void zeroSampleBucketsAreNotNaN() {
        Map<String, Map<CheatTierName, Integer>> hits = new LinkedHashMap<>();
        hits.put("sim.made-up", tierCounts(0, 0, 0, 0));
        EnumMap<CheatTierName, Integer> samplesByTier = new EnumMap<>(CheatTierName.class);
        samplesByTier.put(CheatTierName.DEFAULT, 36);
        TestRun run = new TestRun("run-partial", "detection-engine", "cheat", null, MetricSet.empty(),
                new DetectionBreakdown(hits, samplesByTier, Map.of(), Map.of()), List.of(), List.of(),
                RunStatus.PASSED, 1L, Instant.EPOCH, 0L);

        DetectionRateReport report = new DetectionRateReportBuilder().build(run, ReportFixtures.CATALOG);

        TierRate min = report.byTier().get(0);
        assertFalse(min.hasSamples());
        assertFalse(Double.isNaN(min.rate()));
        assertEquals(0.0, min.rate(), DELTA);
        assertEquals("无样本", min.text());

        // 样本库里没有的 check：没有任何样本支撑，不能显示成 0% 检出
        RateRow unknown = ReportFixtures.rowOf(report.byCheck(), "sim.made-up");
        assertFalse(unknown.hasSamples());
        assertEquals(0.0, unknown.rate(), DELTA);
        assertEquals("无样本", unknown.counts());

        assertTrue(report.toMarkdown().contains("无样本"));
    }

    @Test
    void realCatalogProducesAllThreeViews() {
        DetectionRateReport report = new DetectionRateReportBuilder().build(
                ReportFixtures.run("run-full", ReportFixtures.fullyDetected(),
                        ReportFixtures.samplesPerTier(36)),
                ReportFixtures.CATALOG);

        assertEquals(15, report.byCheck().size());
        assertEquals(6, report.byClient().size());
        assertEquals(4, report.byTier().size());
        assertEquals(144, report.totalSamples());
        assertEquals(144, report.totalHits());
        assertEquals(1.0, report.overallRate(), DELTA);
        assertTrue(report.byCheck().stream().allMatch(RateRow::hasSamples));
        assertTrue(report.byClient().stream().allMatch(RateRow::hasSamples));

        String markdown = report.toMarkdown();
        assertTrue(markdown.contains("按检测项"));
        assertTrue(markdown.contains("按客户端"));
        assertTrue(markdown.contains("按档位"));
        assertTrue(markdown.contains("sim.speed"));
        assertTrue(markdown.contains("Wurst"));
    }

    @Test
    void markdownCarriesHeadersAndNumbers() {
        DetectionRateReport report = new DetectionRateReportBuilder().build(
                ReportFixtures.run("run-md", ReportFixtures.detected(CheatType.SPEED, 1, 0, 1, 1),
                        ReportFixtures.samplesPerTier(36)),
                ReportFixtures.CATALOG);

        String markdown = report.toMarkdown();
        assertTrue(markdown.contains("## 检出率报告（运行 run-md）"));
        assertTrue(markdown.contains("总样本 144，命中 3"));
        assertTrue(markdown.contains("### 按检测项"));
        assertTrue(markdown.contains("### 按客户端"));
        assertTrue(markdown.contains("### 按档位"));
        assertTrue(markdown.contains("3/16"));
        assertTrue(markdown.contains("18.8%"));
    }

    private static EnumMap<CheatTierName, Integer> tierCounts(int min, int def, int max, int hidden) {
        EnumMap<CheatTierName, Integer> counts = new EnumMap<>(CheatTierName.class);
        counts.put(CheatTierName.MIN, min);
        counts.put(CheatTierName.DEFAULT, def);
        counts.put(CheatTierName.MAX, max);
        counts.put(CheatTierName.HIDDEN, hidden);
        return counts;
    }
}
