package io.laplace.sprite.cheats.report;

import io.laplace.sprite.core.model.CheatType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 绕过清单（v1.1.0 §4.2）：已实现但被绕过的检测项，以及引擎尚未实现对应检测项的类型。
 *
 * <p>两类 backlog 分开承载：{@link #entries()} 是「已实现但被绕过」，
 * {@link #undetectableTypes()} 是「引擎尚未实现该检测项」，Markdown 里也分节输出。</p>
 *
 * <p>通用异常样本不属于这里：它们本就不期望被检出，未命中是功能性失败，
 * 应看「未覆盖项清单」与功能冒烟结果，而不是绕过清单。</p>
 *
 * @param entries           绕过记录
 * @param undetectableTypes 锚点标记为不可检出、目标引擎尚无对应 check 的类型
 */
public record BypassList(List<BypassEntry> entries, List<CheatType> undetectableTypes) {

    public BypassList {
        entries = List.copyOf(entries);
        undetectableTypes = List.copyOf(undetectableTypes);
    }

    public static BypassList empty() {
        return new BypassList(List.of(), List.of());
    }

    /** 绕过记录条数（不含 {@link #undetectableTypes()}）。 */
    public int size() {
        return entries.size();
    }

    /** 按作弊类型分组，组内保持 {@link #severityOrdered()} 的顺序。 */
    public Map<CheatType, List<BypassEntry>> byCheatType() {
        Map<CheatType, List<BypassEntry>> grouped = new EnumMap<>(CheatType.class);
        for (BypassEntry entry : severityOrdered()) {
            grouped.computeIfAbsent(entry.cheatType(), ignored -> new ArrayList<>()).add(entry);
        }
        Map<CheatType, List<BypassEntry>> result = new EnumMap<>(CheatType.class);
        grouped.forEach((type, list) -> result.put(type, List.copyOf(list)));
        return Collections.unmodifiableMap(result);
    }

    /** 最严重的排前面：全部档位未命中 > 部分漏检；同级按样本数降序，再按类型序、档位序。 */
    public List<BypassEntry> severityOrdered() {
        List<BypassEntry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator
                .comparing(BypassEntry::neverDetected).reversed()
                .thenComparing(Comparator.comparingInt(BypassEntry::samples).reversed())
                .thenComparing(BypassEntry::cheatType)
                .thenComparing(BypassEntry::tier));
        return List.copyOf(sorted);
    }

    /** 中文 Markdown 分节：绕过清单 + 引擎尚未实现的检测项。 */
    public String toMarkdown() {
        StringBuilder out = new StringBuilder();
        out.append("### 绕过清单（已实现但被绕过）\n\n");
        if (entries.isEmpty()) {
            out.append("无。所有预期检出的档位均至少命中一次。\n\n");
        } else {
            out.append("| 作弊类型 | 出处 | 档位 | 命中/样本 | 严重度 | 建议 |\n");
            out.append("| --- | --- | --- | --- | --- | --- |\n");
            for (BypassEntry entry : severityOrdered()) {
                out.append("| ").append(entry.cheatType())
                        .append(" | ").append(String.join("/", entry.sources()))
                        .append(" | ").append(entry.tier())
                        .append(" | ").append(entry.hits()).append('/').append(entry.samples())
                        .append(" | ").append(entry.severity())
                        .append(" | ").append(entry.recommendation())
                        .append(" |\n");
            }
            out.append('\n');
        }
        out.append("### 引擎尚未实现该检测项\n\n");
        if (undetectableTypes.isEmpty()) {
            out.append("无。样本库中的作弊类型在当前目标引擎均有对应 check。\n");
        } else {
            out.append("以下类型在样本库中标记为不可检出，属于「引擎尚未实现该检测项」，")
                    .append("与上面的「已实现但被绕过」是两类不同的 backlog，不混在同一张表里。\n\n");
            out.append("| 作弊类型 | 说明 |\n");
            out.append("| --- | --- |\n");
            for (CheatType type : undetectableTypes) {
                out.append("| ").append(type)
                        .append(" | 目标引擎尚无对应 check，暂不计入绕过清单 |\n");
            }
        }
        return out.toString();
    }
}
