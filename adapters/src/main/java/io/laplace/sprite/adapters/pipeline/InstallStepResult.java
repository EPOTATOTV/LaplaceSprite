package io.laplace.sprite.adapters.pipeline;

import java.util.List;

/**
 * 单个装配阶段的执行结果（14.5）。
 *
 * @param stage      阶段
 * @param ok         是否通过
 * @param detail     人可读结论
 * @param warnings   失败原因或非致命告警
 * @param durationMs 耗时（毫秒）
 */
public record InstallStepResult(InstallStage stage, boolean ok, String detail, List<String> warnings,
                                long durationMs) {

    public InstallStepResult {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static InstallStepResult ok(InstallStage stage, String detail, long durationMs) {
        return new InstallStepResult(stage, true, detail, List.of(), durationMs);
    }

    public static InstallStepResult failed(InstallStage stage, String detail, List<String> warnings,
                                           long durationMs) {
        return new InstallStepResult(stage, false, detail, warnings, durationMs);
    }
}
