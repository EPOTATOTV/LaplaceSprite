package io.laplace.sprite.loadtest;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadCurveRendererTest {

    @Test
    void markdownContainsEveryScenarioAndStep() {
        Map<String, LoadVerdict> byScenario = new LinkedHashMap<>();
        byScenario.put("walk", new LoadVerdict(true, null, List.of(), List.of(
                new LoadVerdict.CurvePoint(10, 19.9, 20.0, true),
                new LoadVerdict.CurvePoint(20, 19.6, 30.0, true))));
        byScenario.put("mine", new LoadVerdict(false, 30, List.of("并发 30：稳态 TPS 19.00 < 19.5"),
                List.of(new LoadVerdict.CurvePoint(10, 19.8, 25.0, true),
                        new LoadVerdict.CurvePoint(20, 19.4, 55.0, false),
                        new LoadVerdict.CurvePoint(30, 19.0, 60.0, false))));

        String markdown = new LoadCurveRenderer().toMarkdown(byScenario);

        assertTrue(markdown.contains("walk TPS"));
        assertTrue(markdown.contains("mine P95(ms)"));
        assertTrue(markdown.contains("| 10 |"));
        assertTrue(markdown.contains("| 20 |"));
        assertTrue(markdown.contains("| 30 |"));
        assertTrue(markdown.contains("通过"));
        assertTrue(markdown.contains("破线"));
    }

    @Test
    void jsonContainsScenarioCurves() {
        Map<String, LoadVerdict> byScenario = new LinkedHashMap<>();
        byScenario.put("walk", new LoadVerdict(true, null, List.of(),
                List.of(new LoadVerdict.CurvePoint(10, 19.9, 20.0, true))));

        String json = new LoadCurveRenderer().toJson(byScenario);

        assertTrue(json.contains("\"walk\""));
        assertTrue(json.contains("\"breakingConcurrency\""));
        assertTrue(json.contains("\"concurrency\" : 10"));
        assertTrue(json.contains("19.9"));
    }
}
