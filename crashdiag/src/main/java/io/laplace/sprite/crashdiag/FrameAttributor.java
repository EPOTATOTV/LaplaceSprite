package io.laplace.sprite.crashdiag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 栈帧归因（15.5，借鉴 modlens-mcp 思路）：栈帧 × 类索引交叉，按帧命中数排序给出嫌疑 mod。
 *
 * <p>只给嫌疑排序，不做绝对定罪：类索引不可能覆盖所有类，覆盖率低时必须显式告警并下调置信度。</p>
 */
public final class FrameAttributor {

    /** 覆盖率低于该值时认定「索引不足」，下调置信度并出告警。 */
    public static final double LOW_COVERAGE_THRESHOLD = 0.5;

    private static final Pattern FRAME = Pattern.compile("at\\s+([A-Za-z0-9_$.]+)\\.[A-Za-z0-9_$<>]+\\([^)]*\\)");

    /** 按帧命中数排序，做嫌疑排序与置信度计算。 */
    public Attribution attribute(String text, ClassIndex index) {
        List<String> classes = frameClasses(text);
        int total = classes.size();
        Map<String, Integer> hits = new LinkedHashMap<>();
        int resolved = 0;
        for (String className : classes) {
            Optional<String> modId = index.modFor(className);
            if (modId.isPresent()) {
                resolved++;
                hits.merge(modId.get(), 1, Integer::sum);
            }
        }
        double coverage = total == 0 ? 0.0 : (double) resolved / total;
        boolean lowCoverage = coverage < LOW_COVERAGE_THRESHOLD;
        int resolvedFrames = resolved;

        List<Suspect> suspects = new ArrayList<>();
        hits.forEach((modId, count) -> suspects.add(new Suspect(modId, count,
                confidence(count, resolvedFrames, coverage, lowCoverage))));
        suspects.sort(Comparator.comparingInt(Suspect::frameHits).reversed()
                .thenComparing(Suspect::modId));

        List<String> warnings = new ArrayList<>();
        if (total == 0) {
            warnings.add("现场文本里没有解析出栈帧，无法做帧归因");
        } else if (lowCoverage) {
            warnings.add("类索引覆盖率仅 %.0f%%（%d/%d 帧可归因），命中数不足以定性，请补充类索引前缀后复判"
                    .formatted(coverage * 100, resolved, total));
        }
        if (!suspects.isEmpty()) {
            warnings.add("嫌疑 mod 按帧命中数排序，仅为嫌疑排序，不构成定论");
        }
        return new Attribution(suspects, coverage, total, resolved, lowCoverage, warnings);
    }

    /** 从文本里抽出所有栈帧的类名，保持出现顺序且去重前不去重（重复帧也算命中）。 */
    public List<String> frameClasses(String text) {
        List<String> classes = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return classes;
        }
        Matcher matcher = FRAME.matcher(text);
        while (matcher.find()) {
            classes.add(matcher.group(1));
        }
        return classes;
    }

    /**
     * 置信度：先在「可归因帧」里算该 mod 的占比，覆盖率偏低时再乘一次覆盖率作为惩罚。
     *
     * <p>不惩罚的话，索引只覆盖一两个类时占比会虚高到 1.0，正是「绝对定罪」的来源。</p>
     */
    private static double confidence(int frameHits, int resolvedFrames, double coverage, boolean lowCoverage) {
        if (resolvedFrames <= 0) {
            return 0.0;
        }
        double share = (double) frameHits / resolvedFrames;
        return lowCoverage ? share * coverage : share;
    }

    /**
     * 一个嫌疑 mod。
     *
     * @param modId      mod / 插件 id
     * @param frameHits  命中的栈帧数
     * @param confidence 置信度，已按覆盖率下调
     */
    public record Suspect(String modId, int frameHits, double confidence) {
    }

    /**
     * 归因结果。
     *
     * @param suspects           嫌疑 mod，按帧命中数降序
     * @param classIndexCoverage 可归因帧占比
     * @param totalFrames        栈帧总数
     * @param resolvedFrames     被索引解析出来的帧数
     * @param lowCoverage        覆盖率是否过低
     * @param warnings           告警
     */
    public record Attribution(List<Suspect> suspects, double classIndexCoverage, int totalFrames,
                              int resolvedFrames, boolean lowCoverage, List<String> warnings) {

        public Attribution {
            suspects = suspects == null ? List.of() : List.copyOf(suspects);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }

        /** 首要嫌疑 mod：没有嫌疑时为空。 */
        public Optional<String> primarySuspect() {
            return suspects.isEmpty() ? Optional.empty() : Optional.of(suspects.get(0).modId());
        }
    }
}
