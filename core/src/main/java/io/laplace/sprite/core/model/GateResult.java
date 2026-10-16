package io.laplace.sprite.core.model;

import java.util.List;

/**
 * 门禁判定结果（第五章 GateResult）。
 */
public record GateResult(boolean passed, List<String> failedMetrics, List<Violation> violations) {

    public GateResult {
        failedMetrics = failedMetrics == null ? List.of() : List.copyOf(failedMetrics);
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public static GateResult pass() {
        return new GateResult(true, List.of(), List.of());
    }

    public static GateResult fail(List<Violation> violations) {
        return new GateResult(false,
                violations.stream().map(Violation::metric).distinct().toList(),
                violations);
    }
}
