package io.laplace.sprite.report;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PACC 夜间趋势（L-10）：误报率 / 检出率计算与 SVG 产出。 */
class DetectionTrendTest {

    @TempDir
    Path tempDir;

    @Test
    void computesRatesFromRawCounts() {
        DetectionTrend trend = DetectionTrend.from(List.of(
                new DetectionTrend.BaselineRun("1.3.0", 10_000, 1, 500, 490),
                new DetectionTrend.BaselineRun("1.4.0", 10_000, 2, 500, 495)));

        assertEquals(2, trend.points().size());
        assertEquals(0.0001, trend.points().get(0).falsePositiveRate(), 1e-9);
        assertEquals(0.98, trend.points().get(0).detectionRate(), 1e-9);
        assertEquals(0.99, trend.points().get(1).detectionRate(), 1e-9);
    }

    @Test
    void rejectsCountsThatExceedTheirDenominator() {
        assertThrows(IllegalArgumentException.class,
                () -> new DetectionTrend.BaselineRun("bad", 10, 11, 10, 5));
        assertThrows(IllegalArgumentException.class,
                () -> new DetectionTrend.BaselineRun("bad", 10, 1, 10, 11));
    }

    @Test
    void writesSvgWithBothSeriesAndVersionLabels(@TempDir Path tempDir) {
        DetectionTrend trend = DetectionTrend.from(List.of(
                new DetectionTrend.BaselineRun("1.3.0", 1000, 1, 400, 390),
                new DetectionTrend.BaselineRun("1.4.0", 1000, 0, 400, 398)));

        String svg = trend.toSvg(640, 240);

        assertTrue(svg.startsWith("<svg"), svg.substring(0, 20));
        assertEquals(2, svg.split("<polyline", -1).length - 1, "两条折线：检出率与误报率");
        assertTrue(svg.contains("1.3.0"));
        assertTrue(svg.contains("1.4.0"));
        assertTrue(svg.contains("#2b4d86") && svg.contains("#c6433a"));

        Path out = tempDir.resolve("trend").resolve("pacc.svg");
        trend.writeSvg(out, 640, 240);
        assertTrue(Files.isRegularFile(out));
    }

    @Test
    void emptyTrendStillProducesAValidChart() {
        String svg = DetectionTrend.from(List.of()).toSvg(400, 200);

        assertTrue(svg.contains("没有基准数据"));
        assertTrue(svg.endsWith("</svg>\n"));
    }

    @Test
    void markdownListsEveryVersion() {
        DetectionTrend trend = DetectionTrend.from(List.of(
                new DetectionTrend.BaselineRun("1.4.0", 1000, 0, 100, 99)));

        String md = trend.toMarkdown();

        assertTrue(md.contains("| 1.4.0 |"));
        assertTrue(md.contains("99.00%"));
    }
}
