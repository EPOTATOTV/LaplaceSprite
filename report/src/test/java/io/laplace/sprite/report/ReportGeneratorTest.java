package io.laplace.sprite.report;

import io.laplace.sprite.core.model.SampleTierName;
import io.laplace.sprite.core.model.CheatType;
import io.laplace.sprite.core.model.DetectionBreakdown;
import io.laplace.sprite.core.model.MetricSet;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.model.RunStatus;
import io.laplace.sprite.core.model.TestRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportGeneratorTest {

    private final ReportGenerator generator = new ReportGenerator();

    @Test
    void producesAllFormatsWithoutExternalResources() {
        List<TestRun> runs = runs();

        assertFalse(generator.toJson(runs).isBlank());
        assertFalse(generator.toMarkdown(runs).isBlank());

        String html = generator.toHtml(runs);
        assertFalse(html.isBlank());
        assertFalse(html.contains("http://"), "HTML 含 http:// 外部引用");
        assertFalse(html.contains("https://"), "HTML 含 https:// 外部引用");
    }

    @Test
    void writeEmitsThreeFiles(@TempDir Path dir) {
        Path out = generator.write(dir, runs());

        assertTrue(Files.exists(out.resolve("report.json")));
        assertTrue(Files.exists(out.resolve("report.md")));
        assertTrue(Files.exists(out.resolve("report.html")));
    }

    private static List<TestRun> runs() {
        Map<String, Map<SampleTierName, Integer>> hitsByCheckTier = new LinkedHashMap<>();
        Map<SampleTierName, Integer> speedHits = new EnumMap<>(SampleTierName.class);
        speedHits.put(SampleTierName.MAX, 18);
        speedHits.put(SampleTierName.DEFAULT, 2);
        hitsByCheckTier.put("speed", speedHits);

        Map<SampleTierName, Integer> samplesByTier = new EnumMap<>(SampleTierName.class);
        samplesByTier.put(SampleTierName.MAX, 18);
        samplesByTier.put(SampleTierName.DEFAULT, 2);

        Map<CheatType, Integer> hitsByType = new EnumMap<>(CheatType.class);
        hitsByType.put(CheatType.SPEED, 20);
        Map<CheatType, Integer> samplesByType = new EnumMap<>(CheatType.class);
        samplesByType.put(CheatType.SPEED, 20);

        DetectionBreakdown breakdown = new DetectionBreakdown(hitsByCheckTier, samplesByTier,
                hitsByType, samplesByType);
        MetricSet metrics = new MetricSet(100, 0, 20, 20, 5, 5, true, 0.82);

        TestRun run = new TestRun("run-1", "detection-engine", "cheat",
                PlatformKey.of(PlatformFamily.PAPER, "1.20.4"), metrics, breakdown,
                List.of(), List.of(), RunStatus.PASSED, 7L, Instant.now(), 1234L);
        return List.of(run);
    }
}
