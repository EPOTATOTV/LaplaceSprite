package io.laplace.sprite.crashdiag;

import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.crashdiag.FrameAttributor.Suspect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 诊断渲染（15.5）：结构化 JSON + 中文 markdown 报告节。
 */
public final class DiagnosisRenderer {

    private final CrashDiagnosis diagnosis;

    public DiagnosisRenderer(CrashDiagnosis diagnosis) {
        this.diagnosis = diagnosis;
    }

    /** 结构化 JSON。字段名与现场包里的 diagnosis.json 一致。 */
    public String toJson() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("id", diagnosis.id());
        root.put("instanceId", diagnosis.instanceId());
        root.put("category", diagnosis.category().name());
        root.put("categoryLabel", diagnosis.category().label());
        root.put("signature", diagnosis.signature());
        root.put("classIndexCoverage", diagnosis.classIndexCoverage());
        root.put("lowCoverage", diagnosis.lowCoverage());
        root.put("collectedAt", diagnosis.collectedAt().toString());
        root.put("suspects", suspects());
        root.put("humanAdvice", diagnosis.humanAdvice());
        root.put("artifacts", CrashDiagnoser.describeArtifacts(diagnosis.artifacts()));
        return Json.write(root);
    }

    /** 中文 markdown，可直接嵌进测试报告的一节。 */
    public String toMarkdown() {
        StringBuilder md = new StringBuilder();
        md.append("## 崩溃诊断（15.5）\n\n");
        md.append("- 实例：").append(diagnosis.instanceId()).append('\n');
        md.append("- 诊断 id：").append(diagnosis.id()).append('\n');
        md.append("- 分类：").append(diagnosis.category().label())
                .append("（").append(diagnosis.category().name()).append("）\n");
        md.append("- 命中签名：").append(diagnosis.signature()).append('\n');
        md.append("- 类索引覆盖率：").append("%.0f%%".formatted(diagnosis.classIndexCoverage() * 100))
                .append(diagnosis.lowCoverage() ? "（偏低，未做绝对定罪）" : "").append('\n');
        md.append("- 采集时间：").append(diagnosis.collectedAt()).append("\n\n");

        md.append("### 嫌疑 mod（按帧命中数排序）\n\n");
        if (diagnosis.suspects().isEmpty()) {
            md.append("未解析出可归因的栈帧。\n\n");
        } else {
            md.append("| mod | 帧命中 | 置信度 |\n| --- | --- | --- |\n");
            for (Suspect suspect : diagnosis.suspects()) {
                md.append("| ").append(suspect.modId())
                        .append(" | ").append(suspect.frameHits())
                        .append(" | ").append("%.2f".formatted(suspect.confidence()))
                        .append(" |\n");
            }
            md.append('\n');
        }

        md.append("### 建议\n\n");
        int index = 1;
        for (String advice : diagnosis.humanAdvice()) {
            md.append(index++).append(". ").append(advice).append('\n');
        }

        md.append("\n### 现场采集\n\n");
        for (Map<String, Object> artifact : CrashDiagnoser.describeArtifacts(diagnosis.artifacts())) {
            md.append("- ").append(Boolean.TRUE.equals(artifact.get("found")) ? "已采集" : "缺失")
                    .append("｜").append(artifact.get("kind"))
                    .append("｜").append(artifact.get("path")).append('\n');
        }
        return md.toString();
    }

    /** 只要建议列表。 */
    public List<String> toHumanAdvice() {
        return diagnosis.humanAdvice();
    }

    private List<Map<String, Object>> suspects() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Suspect suspect : diagnosis.suspects()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("modId", suspect.modId());
            row.put("frameHits", suspect.frameHits());
            row.put("confidence", suspect.confidence());
            rows.add(row);
        }
        return rows;
    }
}
