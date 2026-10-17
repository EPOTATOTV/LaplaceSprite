package io.laplace.sprite.mixin;

import java.util.List;

/**
 * 注入计数门禁结果（15.11）。
 *
 * @param passed   是否放行；任何一条硬失败都为 false
 * @param failures 硬失败（require 数量不符、启动日志里的注入异常）
 * @param warnings 软告警（expect 数量不符）
 */
public record InjectionGateResult(boolean passed, List<InjectionFailure> failures, List<String> warnings) {

    public InjectionGateResult {
        failures = failures == null ? List.of() : List.copyOf(failures);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /** 是否阻塞 CI 门禁。 */
    public boolean blocked() {
        return !passed;
    }
}
