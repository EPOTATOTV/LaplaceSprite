package io.laplace.sprite.adapters.pipeline;

import java.util.List;
import java.util.Optional;

/**
 * 五步装配流水线的总结果（14.5）。
 *
 * @param instanceId 实例标识（适配器 + 平台键）
 * @param steps      已执行的阶段结果；未执行的阶段不会出现
 * @param allOk      是否全部通过
 */
public record InstallPipelineResult(String instanceId, List<InstallStepResult> steps, boolean allOk) {

    public InstallPipelineResult {
        steps = List.copyOf(steps);
    }

    /** 第一个未通过的阶段；全部通过时为空。 */
    public Optional<InstallStage> failedStage() {
        return steps.stream().filter(step -> !step.ok()).map(InstallStepResult::stage).findFirst();
    }

    /** 某阶段的执行结果；未执行时为空。 */
    public Optional<InstallStepResult> stage(InstallStage stage) {
        return steps.stream().filter(step -> step.stage() == stage).findFirst();
    }

    /** 装配结论（Markdown），供报告与 CI 日志直接引用。 */
    public String toMarkdown() {
        StringBuilder out = new StringBuilder();
        out.append("装配流水线 `").append(instanceId).append("`：")
                .append(allOk ? "通过" : "失败");
        failedStage().ifPresent(stage -> out.append("（失败阶段：").append(stage.name()).append("）"));
        out.append("\n\n| 阶段 | 结果 | 耗时 | 结论 |\n|-|-|-|-|\n");
        for (InstallStepResult step : steps) {
            out.append("| ").append(step.stage().name())
                    .append(" | ").append(step.ok() ? "通过" : "失败")
                    .append(" | ").append(step.durationMs()).append("ms")
                    .append(" | ").append(step.detail()).append(" |\n");
        }
        List<String> warnings = steps.stream().flatMap(step -> step.warnings().stream()).toList();
        if (!warnings.isEmpty()) {
            out.append("\n告警：\n");
            warnings.forEach(warning -> out.append("- ").append(warning).append('\n'));
        }
        return out.toString();
    }
}
