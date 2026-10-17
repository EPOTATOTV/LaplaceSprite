package io.laplace.sprite.cheats.report;

import io.laplace.sprite.cheats.SampleCategory;

import java.util.List;

/**
 * 断言结果三视图（4.2）：按目标 / 按样本类型 / 按档位，外加运行 id、样本合计与未覆盖项。
 *
 * @param runId        运行 id
 * @param totalSamples 注入样本总数（各档位样本之和）
 * @param totalHits    命中总数
 * @param byTarget     按目标视图（桶键为 {@code TestRun.target()}）
 * @param bySampleType 按样本类型视图（作弊子类逐型分桶，通用异常按 sampleType 分桶）
 * @param byTier       按档位视图
 * @param uncovered    未覆盖项清单（声明了但本次未注入的档位）
 */
public record DetectionRateReport(
        String runId,
        int totalSamples,
        int totalHits,
        List<RateRow> byTarget,
        List<RateRow> bySampleType,
        List<TierRate> byTier,
        List<UncoveredEntry> uncovered) {

    public DetectionRateReport {
        byTarget = List.copyOf(byTarget);
        bySampleType = List.copyOf(bySampleType);
        byTier = List.copyOf(byTier);
        uncovered = List.copyOf(uncovered);
    }

    public double overallRate() {
        return totalSamples <= 0 ? 0.0 : (double) totalHits / totalSamples;
    }

    /** 三视图与未覆盖项的中文 Markdown，供报告层 / CLI 直接嵌入。 */
    public String toMarkdown() {
        StringBuilder out = new StringBuilder();
        out.append("## 检出率报告（运行 ").append(runId).append("）\n\n");
        out.append("总样本 ").append(totalSamples)
                .append("，命中 ").append(totalHits)
                .append("，检出率 ").append(totalSamples <= 0 ? "无样本" : RateFormat.percent(overallRate()))
                .append("\n\n");

        out.append("### 按目标\n\n");
        appendMatrix(out, "目标", byTarget);
        out.append("### 按样本类型\n\n");
        appendMatrix(out, "样本类型", bySampleType);
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
        out.append('\n');
        appendUncovered(out);
        return out.toString();
    }

    private void appendUncovered(StringBuilder out) {
        out.append("### 未覆盖项清单\n\n");
        if (uncovered.isEmpty()) {
            out.append("无。本次运行已覆盖全部声明档位。\n");
            return;
        }
        out.append("以下样本的声明档位未出现在本次运行中，按类别分组，作为被测目标的改进 backlog。")
                .append("非本套件覆盖的类别会整体列在这里，属于矩阵覆盖缺口而非检出失败。\n\n");
        for (SampleCategory category : SampleCategory.values()) {
            List<UncoveredEntry> group = uncovered.stream()
                    .filter(entry -> entry.category() == category)
                    .toList();
            if (group.isEmpty()) {
                continue;
            }
            out.append("#### ").append(category).append("\n\n");
            out.append("| 样本 | 样本类型 | 未注入档位 |\n");
            out.append("| --- | --- | --- |\n");
            for (UncoveredEntry entry : group) {
                out.append("| ").append(entry.sampleId())
                        .append(" | ").append(entry.sampleType())
                        .append(" | ").append(String.join(", ",
                                entry.missingTiers().stream().map(Enum::name).toList()))
                        .append(" |\n");
            }
            out.append('\n');
        }
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
