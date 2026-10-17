package io.laplace.sprite.datapack;

import java.util.List;

/**
 * 数据包冒烟用例的执行结果（15.8）。
 *
 * @param caseId         用例 id
 * @param steps          按顺序下发过的命令与回显
 * @param enabled        目标包是否出现在 {@code /datapack list enabled}
 * @param functionOutput {@code /function} 的完整回显
 * @param scoreboard     从回显里摘出的记分板行
 * @param passed         是否通过
 * @param failedStep     首个失败步骤的命令；通过时为 null
 * @param notes          附注（回退后端、控制台不可用等）
 */
public record DatapackRunResult(String caseId, List<DatapackStep> steps, boolean enabled,
                                String functionOutput, String scoreboard, boolean passed,
                                String failedStep, String notes) {

    public DatapackRunResult {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public List<String> commands() {
        return steps.stream().map(DatapackStep::command).toList();
    }
}
