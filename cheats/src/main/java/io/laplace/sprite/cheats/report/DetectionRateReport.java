package io.laplace.sprite.cheats.report;

import java.util.List;

/**
 * 检出率报告三视图（v1.1.0 §4.2）：按检测项 / 按客户端 / 按档位，外加运行 id 与样本合计。
 *
 * @param runId        运行 id
 * @param totalSamples 注入样本总数（各档位样本之和）
 * @param totalHits    命中总数
 * @param byCheck      按检测项视图
 * @param byClient     按客户端视图
 * @param byTier       按档位视图
 */
public record DetectionRateReport(
        String runId,
        int totalSamples,
        int totalHits,
        List<RateRow> byCheck,
        List<RateRow> byClient,
        List<TierRate> byTier) {

    public DetectionRateReport {
        byCheck = List.copyOf(byCheck);
        byClient = List.copyOf(byClient);
        byTier = List.copyOf(byTier);
    }

    public double overallRate() {
        return totalSamples <= 0 ? 0.0 : (double) totalHits / totalSamples;
    }

    /** 三个中文 Markdown 表格，供报告层 / CLI 直接嵌入。 */
    public String toMarkdown() {
        StringBuilder out = new StringBuilder();
        out.append("## 检出率报告（运行 ").append(runId).append("）\n\n");
        out.append("总样本 ").append(totalSamples)
                .append("，命中 ").append(totalHits)
                .append("，检出率 ").append(totalSamples <= 0 ? "无样本" : RateFormat.percent(overallRate()))
                .append("\n\n");

        out.append("### 按检测项\n\n");
        appendMatrix(out, "检测项", byCheck);
        out.append("### 按客户端\n\n");
        appendMatrix(out, "客户端", byClient);
        out.append("### 按档位\n\n");
        out.append("| 档位 | 命中 | 样本 | 检出率 |\n");
        out.append("| --- | --- | --- | --- |\n");
        for (TierRate tier : byTier) {
            out.append("| ").append(tier.tier())
                    .append(" | ").append(tier.hits())
                    .append(" | ").append(tier.samples())
                    .append(" | ").append(tier.hasSamples() ? RateFormat.percent(tier.rate()) : "无样本")
                    .append(" |\n");
        }
        return out.toString();
    }

    private static void appendMatrix(StringBuilder out, String label, List<RateRow> rows) {
        out.append("| ").append(label).append(" | MIN | DEFAULT | MAX | HIDDEN | 合计 | 检出率 |\n");
        out.append("| --- | --- | --- | --- | --- | --- | --- |\n");
        for (RateRow row : rows) {
            out.append("| ").append(row.label());
            for (TierRate tier : row.tiers()) {
                out.append(" | ").append(tier.text());
            }
            out.append(" | ").append(row.counts())
                    .append(" | ").append(row.hasSamples() ? RateFormat.percent(row.rate()) : "无样本")
                    .append(" |\n");
        }
        out.append('\n');
    }
}
