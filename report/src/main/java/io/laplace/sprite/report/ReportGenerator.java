package io.laplace.sprite.report;

import com.fasterxml.jackson.databind.util.RawValue;

import io.laplace.sprite.core.engine.MetricAggregator;
import io.laplace.sprite.core.engine.ThresholdGate;
import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.core.model.SampleTierName;
import io.laplace.sprite.core.model.CheatType;
import io.laplace.sprite.core.model.GateResult;
import io.laplace.sprite.core.model.MetricSet;
import io.laplace.sprite.core.model.RunStatus;
import io.laplace.sprite.core.model.TestRun;
import io.laplace.sprite.core.model.Thresholds;
import io.laplace.sprite.core.model.Violation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 报告生成（4.6 报告与证据链）：把 {@link TestRun} 列表输出为 JSON / 中文 Markdown / 自包含 HTML。
 */
public final class ReportGenerator {

    private static final String PASS = "#1f9d6b";
    private static final String INFO = "#2f6fed";
    private static final String FAIL = "#d64545";
    private static final String WARN = "#c98a12";
    private static final String SAMPLE = "#7a5af0";

    public String toJson(List<TestRun> runs) {
        return Json.write(runs);
    }

    /**
     * 带附加节的 JSON：顶层变为 {@code {runs, sections}}。
     *
     * <p>形状与不带节时不同是刻意的：{@code laplace gate} 按数组读 report.json，只有用到附加节的新命令
     * 才写对象形状。</p>
     */
    public String toJson(List<TestRun> runs, List<ReportSection> sections) {
        List<ReportSection> safe = sections == null ? List.of() : sections;
        if (safe.isEmpty()) {
            return Json.write(runs);
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("runs", runs);
        List<Map<String, Object>> entries = new ArrayList<>(safe.size());
        for (ReportSection section : safe) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", section.id());
            entry.put("title", section.title());
            entry.put("json", new RawValue(section.toJson()));
            entries.add(entry);
        }
        report.put("sections", entries);
        return Json.write(report);
    }

    public String toMarkdown(List<TestRun> runs) {
        MetricSet total = MetricAggregator.sum(runs);
        GateResult gate = new ThresholdGate().evaluate(total, Thresholds.defaults());

        StringBuilder md = new StringBuilder();
        md.append("# 拉普拉斯精灵 · 测试报告\n\n");

        md.append("## 概览\n\n");
        md.append("| 指标 | 值 |\n| --- | --- |\n");
        md.append("| 运行数 | ").append(runs.size()).append(" |\n");
        md.append("| 合法样本 | ").append(total.legitPlayers()).append(" |\n");
        md.append("| 作弊样本 | ").append(total.cheatSamples()).append(" |\n");
        md.append("| 误报率 | ").append(pct(total.falsePositiveRate())).append(" |\n");
        md.append("| 检出率 | ").append(pct(total.detectionRate())).append(" |\n");
        md.append("| 门禁 | ").append(gate.passed() ? "通过" : "不通过").append(" |\n\n");

        if (!gate.passed()) {
            md.append("门禁未通过项：\n\n");
            for (Violation v : gate.violations()) {
                md.append("- ").append(v.message()).append('\n');
            }
            md.append('\n');
        }

        md.append("## 运行明细\n\n");
        md.append("| 运行 | 目标 | 套件 | 合法样本 | 误报率 | 检出率 | 边界样本 | 状态 | 平台 | 耗时(ms) |\n");
        md.append("| --- | --- | --- | ---: | ---: | ---: | ---: | --- | --- | ---: |\n");
        for (TestRun run : runs) {
            MetricSet m = run.metrics();
            md.append("| ").append(run.id())
                    .append(" | ").append(run.target())
                    .append(" | ").append(run.suite())
                    .append(" | ").append(m.legitPlayers())
                    .append(" | ").append(pct(m.falsePositiveRate()))
                    .append(" | ").append(pct(m.detectionRate()))
                    .append(" | ").append(m.boundarySamples())
                    .append(" | ").append(run.status())
                    .append(" | ").append(run.platform() == null ? "-" : run.platform().id())
                    .append(" | ").append(run.durationMs())
                    .append(" |\n");
        }
        md.append('\n');

        md.append("## 检出率 · 按档位\n\n");
        md.append("| 档位 | 检出率 |\n| --- | ---: |\n");
        Map<SampleTierName, Double> byTier = MetricAggregator.detectionRateByTier(runs);
        for (SampleTierName tier : SampleTierName.values()) {
            Double rate = byTier.get(tier);
            if (rate != null) {
                md.append("| ").append(tier).append(" | ").append(pct(rate)).append(" |\n");
            }
        }
        md.append('\n');

        md.append("## 检出率 · 按检测项 × 档位\n\n");
        md.append("| 检测项 | MIN | DEFAULT | MAX | HIDDEN |\n| --- | ---: | ---: | ---: | ---: |\n");
        Map<String, Map<SampleTierName, Double>> byCheck = MetricAggregator.detectionRateByCheck(runs);
        for (Map.Entry<String, Map<SampleTierName, Double>> entry : byCheck.entrySet()) {
            md.append("| ").append(entry.getKey());
            for (SampleTierName tier : SampleTierName.values()) {
                Double rate = entry.getValue().get(tier);
                md.append(" | ").append(rate == null ? "-" : pct(rate));
            }
            md.append(" |\n");
        }
        md.append('\n');

        md.append("## 检出率 · 按作弊类型\n\n");
        md.append("| 作弊类型 | 检出率 |\n| --- | ---: |\n");
        MetricAggregator.detectionRateByCheatType(runs)
                .forEach((type, rate) -> md.append("| ").append(type).append(" | ").append(pct(rate)).append(" |\n"));
        md.append('\n');

        return md.toString();
    }

    /** 基础报告之后逐节追加附加节（Markdown 原文照搬，写什么由功能轨自己负责）。 */
    public String toMarkdown(List<TestRun> runs, List<ReportSection> sections) {
        StringBuilder md = new StringBuilder(toMarkdown(runs));
        for (ReportSection section : sections == null ? List.<ReportSection>of() : sections) {
            md.append("## ").append(section.title()).append("\n\n");
            md.append(section.toMarkdown()).append("\n\n");
        }
        return md.toString();
    }

    public String toHtml(List<TestRun> runs) {
        return toHtml(runs, List.of());
    }

    public String toHtml(List<TestRun> runs, List<ReportSection> sections) {
        MetricSet total = MetricAggregator.sum(runs);
        GateResult gate = new ThresholdGate().evaluate(total, Thresholds.defaults());
        Map<SampleTierName, Double> byTier = MetricAggregator.detectionRateByTier(runs);
        Map<String, Map<SampleTierName, Double>> byCheck = MetricAggregator.detectionRateByCheck(runs);
        Map<CheatType, Double> byType = MetricAggregator.detectionRateByCheatType(runs);

        StringBuilder h = new StringBuilder();
        h.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n")
                .append("<meta charset=\"utf-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
                .append("<title>拉普拉斯精灵 · 测试报告</title>\n")
                .append("<style>\n").append(css()).append("</style>\n</head>\n<body>\n");

        h.append("<header class=\"hero\">\n")
                .append("<p class=\"kicker\">拉普拉斯精灵 · 反作弊测试平台</p>\n")
                .append("<h1>测试报告</h1>\n")
                .append("<p class=\"sub\">生成时间 ").append(esc(Instant.now().toString()))
                .append(" · 共 ").append(runs.size()).append(" 次运行</p>\n")
                .append("</header>\n");

        h.append("<section class=\"cards\">\n");
        h.append(card("运行数", String.valueOf(runs.size()), INFO, "0"));
        h.append(card("合法样本", String.valueOf(total.legitPlayers()), PASS, "1"));
        h.append(card("作弊样本", String.valueOf(total.cheatSamples()), SAMPLE, "2"));
        h.append(card("误报率", pct(total.falsePositiveRate()), FAIL, "3"));
        h.append(card("检出率", pct(total.detectionRate()), INFO, "1"));
        h.append(card("门禁", gate.passed() ? "通过" : "不通过", gate.passed() ? PASS : FAIL, "2"));
        h.append("</section>\n");

        h.append("<section class=\"panel gate ").append(gate.passed() ? "ok" : "bad").append("\">\n")
                .append("<h2>门禁判定</h2>\n")
                .append("<p class=\"gate-line\">")
                .append(gate.passed() ? "全部指标满足门禁阈值" : "存在未通过指标")
                .append("</p>\n");
        if (!gate.passed()) {
            h.append("<ul class=\"violations\">\n");
            for (Violation v : gate.violations()) {
                h.append("<li>").append(esc(v.message())).append("</li>\n");
            }
            h.append("</ul>\n");
        }
        h.append("</section>\n");

        h.append("<section class=\"panel\">\n<h2>运行明细</h2>\n<table>\n")
                .append("<thead><tr><th>运行</th><th>目标</th><th>套件</th><th class=\"num\">合法样本</th>")
                .append("<th class=\"num\">误报率</th><th class=\"num\">检出率</th><th class=\"num\">边界样本</th>")
                .append("<th>状态</th><th>平台</th><th class=\"num\">耗时</th></tr></thead>\n<tbody>\n");
        for (TestRun run : runs) {
            MetricSet m = run.metrics();
            h.append("<tr><td>").append(esc(run.id())).append("</td>")
                    .append("<td>").append(esc(run.target())).append("</td>")
                    .append("<td>").append(esc(run.suite())).append("</td>")
                    .append("<td class=\"num\">").append(m.legitPlayers()).append("</td>")
                    .append("<td class=\"num\">").append(pct(m.falsePositiveRate())).append("</td>")
                    .append("<td class=\"num\">").append(pct(m.detectionRate())).append("</td>")
                    .append("<td class=\"num\">").append(m.boundarySamples()).append("</td>")
                    .append("<td><span class=\"chip ").append(statusClass(run.status())).append("\">")
                    .append(run.status()).append("</span></td>")
                    .append("<td>").append(run.platform() == null ? "-" : esc(run.platform().id())).append("</td>")
                    .append("<td class=\"num\">").append(run.durationMs()).append("</td></tr>\n");
        }
        h.append("</tbody>\n</table>\n</section>\n");

        h.append("<section class=\"panel\">\n<h2>检出率 · 按档位</h2>\n<div class=\"bars\">\n");
        for (SampleTierName tier : SampleTierName.values()) {
            Double rate = byTier.get(tier);
            if (rate == null) {
                continue;
            }
            h.append(barRow(tier.name(), rate, INFO));
        }
        h.append("</div>\n</section>\n");

        h.append("<section class=\"panel\">\n<h2>检出率 · 按检测项 × 档位</h2>\n<table class=\"matrix\">\n")
                .append("<thead><tr><th>检测项</th>");
        for (SampleTierName tier : SampleTierName.values()) {
            h.append("<th class=\"num\">").append(tier).append("</th>");
        }
        h.append("</tr></thead>\n<tbody>\n");
        for (Map.Entry<String, Map<SampleTierName, Double>> entry : byCheck.entrySet()) {
            h.append("<tr><td>").append(esc(entry.getKey())).append("</td>");
            for (SampleTierName tier : SampleTierName.values()) {
                Double rate = entry.getValue().get(tier);
                h.append("<td class=\"num\">");
                if (rate == null) {
                    h.append("-");
                } else {
                    h.append(bar(rate, 84, INFO)).append(" <span>").append(pct(rate)).append("</span>");
                }
                h.append("</td>");
            }
            h.append("</tr>\n");
        }
        h.append("</tbody>\n</table>\n</section>\n");

        h.append("<section class=\"panel\">\n<h2>检出率 · 按作弊类型</h2>\n<div class=\"bars\">\n");
        for (Map.Entry<CheatType, Double> entry : byType.entrySet()) {
            h.append(barRow(entry.getKey().name(), entry.getValue(), SAMPLE));
        }
        h.append("</div>\n</section>\n");

        for (ReportSection section : sections == null ? List.<ReportSection>of() : sections) {
            h.append("<section class=\"panel\">\n<h2>").append(esc(section.title())).append("</h2>\n")
                    .append("<pre class=\"section-body\">").append(esc(section.toMarkdown()))
                    .append("</pre>\n</section>\n");
        }

        h.append("<footer class=\"foot\">拉普拉斯精灵 · 报告与证据链</footer>\n</body>\n</html>\n");
        return h.toString();
    }

    public Path write(Path directory, List<TestRun> runs) {
        return write(directory, runs, List.of());
    }

    /**
     * 写出手册三件套，并把附加节并入 {@code report.md} / {@code report.html} / {@code report.json}，
     * 同时在 {@code sections/} 下按节各留一份 Markdown 与 JSON 便于单独引用。
     */
    public Path write(Path directory, List<TestRun> runs, List<ReportSection> sections) {
        List<ReportSection> safe = sections == null ? List.of() : sections;
        try {
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("report.json"), toJson(runs, safe), StandardCharsets.UTF_8);
            Files.writeString(directory.resolve("report.md"), toMarkdown(runs, safe), StandardCharsets.UTF_8);
            Files.writeString(directory.resolve("report.html"), toHtml(runs, safe), StandardCharsets.UTF_8);
            if (!safe.isEmpty()) {
                Path sectionDir = directory.resolve("sections");
                Files.createDirectories(sectionDir);
                for (ReportSection section : safe) {
                    String base = safeId(section.id());
                    Files.writeString(sectionDir.resolve(base + ".md"), section.toMarkdown(),
                            StandardCharsets.UTF_8);
                    Files.writeString(sectionDir.resolve(base + ".json"), section.toJson(),
                            StandardCharsets.UTF_8);
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return directory;
    }

    /** 节 id 用作文件名，非安全字符统一替换成下划线。 */
    private static String safeId(String id) {
        return id == null || id.isBlank() ? "section" : id.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String card(String label, String value, String accent, String level) {
        return "<article class=\"card lv" + level + "\" style=\"--accent: " + accent + "\">\n"
                + "<span class=\"card-label\">" + esc(label) + "</span>\n"
                + "<span class=\"card-value\">" + esc(value) + "</span>\n"
                + "</article>\n";
    }

    private static String barRow(String label, double rate, String color) {
        return "<div class=\"bar-row\"><span class=\"bar-label\">" + esc(label) + "</span>"
                + bar(rate, 300, color)
                + "<span class=\"bar-value\">" + pct(rate) + "</span></div>\n";
    }

    /** 手写内联 SVG 条：不引用外部资源，故不带 xmlns。 */
    private static String bar(double rate, double width, String color) {
        double clamped = Math.max(0.0, Math.min(1.0, rate));
        double filled = clamped * width;
        return "<svg class=\"bar\" viewBox=\"0 0 " + num(width) + " 12\" preserveAspectRatio=\"none\">"
                + "<rect width=\"" + num(width) + "\" height=\"12\" rx=\"6\" fill=\"#e7edf6\"/>"
                + "<rect width=\"" + num(filled) + "\" height=\"12\" rx=\"6\" fill=\"" + color + "\"/>"
                + "</svg>";
    }

    private static String statusClass(RunStatus status) {
        return switch (status) {
            case PASSED -> "pass";
            case FAILED -> "fail";
            case RUNNING -> "info";
            case QUEUED, ABORTED, SKIPPED -> "warn";
        };
    }

    private static String pct(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value * 100.0);
    }

    private static String num(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String esc(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String css() {
        return """
                :root {
                  --ink: #0b1b3a;
                  --ink-soft: #4a5878;
                  --line: rgba(11, 27, 58, .10);
                  --pass: #1f9d6b;
                  --info: #2f6fed;
                  --fail: #d64545;
                  --warn: #c98a12;
                  --sample: #7a5af0;
                }
                * { box-sizing: border-box; }
                body {
                  margin: 0;
                  padding: 44px 30px 68px;
                  color: var(--ink);
                  font-family: "Segoe UI", "Microsoft YaHei", system-ui, sans-serif;
                  background:
                    radial-gradient(1100px 520px at 10% -10%, rgba(47, 111, 237, .16), transparent 60%),
                    radial-gradient(900px 460px at 98% 2%, rgba(122, 90, 240, .13), transparent 62%),
                    linear-gradient(162deg, #ffffff 0%, #f2f6fc 100%);
                  min-height: 100vh;
                }
                .hero { max-width: 1080px; margin: 0 auto 30px; }
                .hero .kicker {
                  margin: 0 0 6px;
                  font-size: 13px;
                  letter-spacing: .18em;
                  text-transform: uppercase;
                  color: var(--info);
                }
                .hero h1 { margin: 0; font-size: 34px; letter-spacing: .01em; }
                .hero .sub { margin: 10px 0 0; color: var(--ink-soft); font-size: 13px; }
                .cards {
                  max-width: 1080px;
                  margin: 0 auto 24px;
                  display: grid;
                  grid-template-columns: repeat(auto-fit, minmax(168px, 1fr));
                  gap: 15px;
                }
                .card {
                  position: relative;
                  padding: 18px 18px 20px;
                  border: 1px solid var(--line);
                  border-radius: 16px;
                  background: linear-gradient(158deg, rgba(255, 255, 255, .82), rgba(255, 255, 255, .5));
                  backdrop-filter: blur(15px);
                  box-shadow: 0 20px 42px -32px rgba(11, 27, 58, .5);
                  overflow: hidden;
                }
                .card::before {
                  content: "";
                  position: absolute;
                  inset: 0 auto 0 0;
                  width: 3px;
                  background: var(--accent);
                }
                .card.lv1 { border-radius: 20px 16px 16px 16px; }
                .card.lv2 { border-radius: 16px 20px 16px 16px; }
                .card.lv3 { border-radius: 16px 16px 20px 16px; }
                .card-label { display: block; font-size: 12px; color: var(--ink-soft); letter-spacing: .05em; }
                .card-value { display: block; margin-top: 8px; font-size: 26px; font-weight: 600; }
                .panel {
                  max-width: 1080px;
                  margin: 0 auto 22px;
                  padding: 22px 24px 24px;
                  border: 1px solid var(--line);
                  border-radius: 18px;
                  background: rgba(255, 255, 255, .6);
                  backdrop-filter: blur(16px);
                  box-shadow: 0 26px 48px -36px rgba(11, 27, 58, .46);
                }
                .panel h2 {
                  margin: 0 0 16px;
                  font-size: 17px;
                  font-weight: 600;
                  letter-spacing: .02em;
                }
                .gate.ok { border-left: 3px solid var(--pass); }
                .gate.bad { border-left: 3px solid var(--fail); }
                .gate-line { margin: 0; color: var(--ink-soft); }
                .violations { margin: 12px 0 0; padding-left: 18px; color: var(--fail); }
                .violations li { margin: 4px 0; }
                table { width: 100%; border-collapse: collapse; font-size: 13px; }
                th, td { padding: 9px 10px; text-align: left; border-bottom: 1px solid var(--line); }
                th { color: var(--ink-soft); font-weight: 600; letter-spacing: .03em; }
                .num { text-align: right; }
                tbody tr:hover { background: rgba(47, 111, 237, .05); }
                .chip {
                  display: inline-block;
                  padding: 2px 9px;
                  border-radius: 10px;
                  font-size: 11px;
                  letter-spacing: .04em;
                  color: #fff;
                }
                .chip.pass { background: var(--pass); }
                .chip.fail { background: var(--fail); }
                .chip.info { background: var(--info); }
                .chip.warn { background: var(--warn); }
                .bars { display: flex; flex-direction: column; gap: 11px; }
                .bar-row { display: grid; grid-template-columns: 120px 1fr 70px; align-items: center; gap: 12px; }
                .bar-label { font-size: 13px; color: var(--ink-soft); }
                .bar-value { font-size: 13px; text-align: right; }
                .bar { width: 100%; height: 12px; display: block; }
                .matrix .bar { width: 84px; height: 12px; display: inline-block; vertical-align: middle; }
                .matrix span { margin-left: 6px; font-size: 12px; color: var(--ink-soft); }
                .section-body {
                  margin: 0;
                  padding: 14px 16px;
                  border: 1px solid var(--line);
                  border-radius: 12px;
                  background: rgba(255, 255, 255, .55);
                  font-family: "Cascadia Mono", Consolas, "Courier New", monospace;
                  font-size: 12.5px;
                  line-height: 1.6;
                  white-space: pre-wrap;
                  word-break: break-word;
                }
                .foot { max-width: 1080px; margin: 30px auto 0; text-align: center; color: var(--ink-soft); font-size: 12px; }
                """;
    }
}
