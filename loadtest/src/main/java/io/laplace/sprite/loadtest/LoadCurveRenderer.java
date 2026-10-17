package io.laplace.sprite.loadtest;

import io.laplace.sprite.core.io.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 曲线渲染（15.9）：并发数 × 行为剧本的二维视图。
 *
 * <p>Markdown 给报告，JSON 给控制台图表；两种输出共用同一组曲线点，口径不会漂移。</p>
 */
public final class LoadCurveRenderer {

    public String toMarkdown(Map<String, LoadVerdict> byScenario) {
        TreeSet<Integer> concurrencies = new TreeSet<>();
        byScenario.values().forEach(verdict -> verdict.curvePoints()
                .forEach(point -> concurrencies.add(point.concurrency())));

        StringBuilder header = new StringBuilder("| 并发");
        StringBuilder divider = new StringBuilder("|---");
        for (String scenarioId : byScenario.keySet()) {
            header.append(" | ").append(scenarioId).append(" TPS | ")
                    .append(scenarioId).append(" P95(ms) | ")
                    .append(scenarioId).append(" 门禁");
            divider.append("|---|---|---");
        }
        header.append(" |");
        divider.append("|");

        StringBuilder markdown = new StringBuilder();
        markdown.append("### 并发 × 行为剧本\n\n");
        markdown.append(header).append('\n').append(divider).append('\n');
        for (int concurrency : concurrencies) {
            StringBuilder row = new StringBuilder("| ").append(concurrency);
            for (LoadVerdict verdict : byScenario.values()) {
                LoadVerdict.CurvePoint point = pointAt(verdict, concurrency);
                if (point == null) {
                    row.append(" | — | — | —");
                } else {
                    row.append(" | ").append("%.2f".formatted(point.tps()))
                            .append(" | ").append("%.2f".formatted(point.msptP95()))
                            .append(" | ").append(point.passed() ? "通过" : "破线");
                }
            }
            markdown.append(row).append(" |\n");
        }
        return markdown.toString();
    }

    public String toJson(Map<String, LoadVerdict> byScenario) {
        List<Map<String, Object>> scenarios = new ArrayList<>();
        for (Map.Entry<String, LoadVerdict> entry : byScenario.entrySet()) {
            LoadVerdict verdict = entry.getValue();
            Map<String, Object> scenario = new LinkedHashMap<>();
            scenario.put("id", entry.getKey());
            scenario.put("passed", verdict.passed());
            scenario.put("breakingConcurrency", verdict.breakingConcurrency());
            scenario.put("violations", verdict.violations());
            List<Map<String, Object>> points = new ArrayList<>();
            for (LoadVerdict.CurvePoint point : verdict.curvePoints()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("concurrency", point.concurrency());
                item.put("tps", point.tps());
                item.put("msptP95", point.msptP95());
                item.put("passed", point.passed());
                points.add(item);
            }
            scenario.put("points", points);
            scenarios.add(scenario);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("scenarios", scenarios);
        return Json.write(root);
    }

    private static LoadVerdict.CurvePoint pointAt(LoadVerdict verdict, int concurrency) {
        for (LoadVerdict.CurvePoint point : verdict.curvePoints()) {
            if (point.concurrency() == concurrency) {
                return point;
            }
        }
        return null;
    }
}
