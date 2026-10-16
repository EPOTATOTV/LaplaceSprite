package io.laplace.sprite.core.model;

import java.util.Map;

/**
 * 单条断言结果（14.4 ⑤ AssertionContract 输出）。
 */
public record AssertionResult(String name, AssertionStatus status, String message, Map<String, Object> details) {

    public AssertionResult {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static AssertionResult passed(String name, String message) {
        return new AssertionResult(name, AssertionStatus.PASSED, message, Map.of());
    }

    public static AssertionResult failed(String name, String message) {
        return new AssertionResult(name, AssertionStatus.FAILED, message, Map.of());
    }

    public static AssertionResult failed(String name, String message, Map<String, Object> details) {
        return new AssertionResult(name, AssertionStatus.FAILED, message, details);
    }

    public static AssertionResult skipped(String name, String message) {
        return new AssertionResult(name, AssertionStatus.SKIPPED, message, Map.of());
    }

    public boolean failed() {
        return status == AssertionStatus.FAILED;
    }
}
