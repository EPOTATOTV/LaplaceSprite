package io.laplace.sprite.cheats.report;

import io.laplace.sprite.core.model.CheatTierName;
import io.laplace.sprite.core.model.CheatType;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 绕过清单的判定、严重度排序与 Markdown 输出。 */
class BypassAnalyzerTest {

    @Test
    void zeroHitExpectedTierBecomesBypass() {
        Map<CheatType, Map<CheatTierName, Integer>> detected = ReportFixtures.fullyDetected();
        detected.get(CheatType.SPEED).put(CheatTierName.DEFAULT, 0);

        BypassList list = new BypassAnalyzer().analyze(
                ReportFixtures.run("run-bypass", detected, ReportFixtures.samplesPerTier(36)),
                ReportFixtures.CATALOG);

        assertEquals(1, list.size());
        BypassEntry entry = list.entries().get(0);
        assertEquals(CheatType.SPEED, entry.cheatType());
        assertEquals(CheatTierName.DEFAULT, entry.tier());
        assertEquals(4, entry.samples());
        assertEquals(0, entry.hits());
        assertFalse(entry.neverDetected());
        assertEquals("部分档位漏检", entry.severity());
        assertTrue(entry.sources().contains("Wurst"));
        assertTrue(entry.recommendation().contains("sim.speed"));
    }

    @Test
    void tiersThatWereNotInjectedAreNotReportedAsBypass() {
        // 只跑 DEFAULT（全部检出）：MIN / MAX / HIDDEN 没有注入，不该被算成绕过
        EnumMap<CheatTierName, Integer> samples = new EnumMap<>(CheatTierName.class);
        samples.put(CheatTierName.DEFAULT, 36);
        Map<CheatType, Map<CheatTierName, Integer>> detected = ReportFixtures.fullyDetected();

        BypassList list = new BypassAnalyzer().analyze(
                ReportFixtures.run("run-default-only", detected, samples), ReportFixtures.CATALOG);

        assertTrue(list.entries().isEmpty(), "只注入 DEFAULT 且全部检出时不应有绕过项");
    }

    @Test
    void partiallyDetectedTypeIsNotReportedAsBypass() {
        // SPEED 每档 4 条样本全部检出（部分档位只检出部分样本），不该产生绕过项
        Map<CheatType, Map<CheatTierName, Integer>> detected = ReportFixtures.fullyDetected();
        for (CheatTierName tier : ReportFixtures.TIERS) {
            detected.get(CheatType.SPEED).put(tier, 4);
        }

        BypassList list = new BypassAnalyzer().analyze(
                ReportFixtures.run("run-multi-flag", detected, ReportFixtures.samplesPerTier(36)),
                ReportFixtures.CATALOG);

        assertTrue(list.entries().isEmpty());
    }

    @Test
    void severityOrderedPutsNeverDetectedFirst() {
        Map<CheatType, Map<CheatTierName, Integer>> detected = ReportFixtures.fullyDetected();
        for (CheatTierName tier : ReportFixtures.TIERS) {
            detected.get(CheatType.SPEED).put(tier, 0);
        }
        detected.get(CheatType.FLY).put(CheatTierName.DEFAULT, 0);

        BypassList list = new BypassAnalyzer().analyze(
                ReportFixtures.run("run-severity", detected, ReportFixtures.samplesPerTier(36)),
                ReportFixtures.CATALOG);

        assertEquals(5, list.size());
        List<BypassEntry> ordered = list.severityOrdered();
        assertEquals(CheatType.SPEED, ordered.get(0).cheatType());
        assertEquals(CheatTierName.MIN, ordered.get(0).tier());
        assertTrue(ordered.get(0).neverDetected());
        assertEquals(CheatType.FLY, ordered.get(ordered.size() - 1).cheatType());
        assertFalse(ordered.get(ordered.size() - 1).neverDetected());

        assertEquals(4, list.byCheatType().get(CheatType.SPEED).size());
        assertEquals(1, list.byCheatType().get(CheatType.FLY).size());
    }

    @Test
    void undetectableTypesStayOutOfBypassEntries() {
        Map<CheatType, Map<CheatTierName, Integer>> detected = ReportFixtures.fullyDetected();
        for (CheatTierName tier : ReportFixtures.TIERS) {
            detected.get(CheatType.XRAY).put(tier, 0);
        }

        BypassList list = new BypassAnalyzer(type -> type == CheatType.XRAY).analyze(
                ReportFixtures.run("run-undetectable", detected, ReportFixtures.samplesPerTier(36)),
                ReportFixtures.CATALOG);

        assertTrue(list.entries().isEmpty());
        assertEquals(List.of(CheatType.XRAY), list.undetectableTypes());

        String markdown = list.toMarkdown();
        assertTrue(markdown.contains("### 绕过清单（已实现但被绕过）"));
        assertTrue(markdown.contains("### 引擎尚未实现该检测项"));
        assertTrue(markdown.contains("XRAY"));
    }

    @Test
    void bypassMarkdownCarriesHeadersAndNumbers() {
        Map<CheatType, Map<CheatTierName, Integer>> detected = ReportFixtures.fullyDetected();
        detected.get(CheatType.SPEED).put(CheatTierName.DEFAULT, 0);

        BypassList list = new BypassAnalyzer().analyze(
                ReportFixtures.run("run-md", detected, ReportFixtures.samplesPerTier(36)),
                ReportFixtures.CATALOG);

        String markdown = list.toMarkdown();
        assertTrue(markdown.contains("### 绕过清单（已实现但被绕过）"));
        assertTrue(markdown.contains("| 作弊类型 | 出处 | 档位 | 命中/样本 | 严重度 | 建议 |"));
        assertTrue(markdown.contains("| SPEED |"));
        assertTrue(markdown.contains("0/4"));
        assertTrue(markdown.contains("部分档位漏检"));
        assertTrue(markdown.contains("### 引擎尚未实现该检测项"));
    }

    @Test
    void fullyDetectedRunHasNoBypass() {
        BypassList list = new BypassAnalyzer().analyze(
                ReportFixtures.run("run-clean", ReportFixtures.fullyDetected(),
                        ReportFixtures.samplesPerTier(36)),
                ReportFixtures.CATALOG);

        assertEquals(0, list.size());
        assertTrue(list.entries().isEmpty());
        assertTrue(list.undetectableTypes().isEmpty());
        assertTrue(list.toMarkdown().contains("无。"));
    }
}
