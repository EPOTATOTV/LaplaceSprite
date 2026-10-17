package io.laplace.sprite.core.model;

import java.util.List;

/**
 * 一类被测目标的测试画像（13.6）。
 *
 * @param type                  目标类型
 * @param testFocus             测试重点
 * @param frameworksAndTools   框架与工具（含代表项目）
 * @param typicalRisks          典型风险
 * @param representativeProjects 代表项目
 * @param sources               来源（项目仓库 / 官方文档 URL）
 */
public record TargetTypeProfile(TargetType type, List<String> testFocus,
                                List<String> frameworksAndTools, List<String> typicalRisks,
                                List<String> representativeProjects, List<String> sources) {

    public TargetTypeProfile {
        testFocus = copy(testFocus);
        frameworksAndTools = copy(frameworksAndTools);
        typicalRisks = copy(typicalRisks);
        representativeProjects = copy(representativeProjects);
        sources = copy(sources);
    }

    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
