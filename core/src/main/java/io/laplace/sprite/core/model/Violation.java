package io.laplace.sprite.core.model;

/**
 * 门禁违规项（第五章 GateResult.violations）。
 */
public record Violation(String metric, String operator, double actual, double threshold, String message) {

    public static Violation above(String metric, double actual, double threshold) {
        return new Violation(metric, "<=", actual, threshold,
                "%s 实测 %.6f 超过上限 %.6f".formatted(metric, actual, threshold));
    }

    public static Violation below(String metric, double actual, double threshold) {
        return new Violation(metric, ">=", actual, threshold,
                "%s 实测 %.6f 低于下限 %.6f".formatted(metric, actual, threshold));
    }

    public static Violation failed(String metric, String message) {
        return new Violation(metric, "==", 0, 1, message);
    }
}
