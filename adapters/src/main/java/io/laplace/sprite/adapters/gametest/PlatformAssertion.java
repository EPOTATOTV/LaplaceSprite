package io.laplace.sprite.adapters.gametest;

import java.util.Objects;

/**
 * 平台无关断言（14.6）：一条用例的名字、期望与结论。
 *
 * @param name        用例名
 * @param expectation 期望（断言表达式或失败消息）
 * @param severity    结论等级
 */
public record PlatformAssertion(String name, String expectation, AssertionSeverity severity) {

    public PlatformAssertion {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(severity, "severity");
        expectation = expectation == null ? "" : expectation;
    }

    public boolean failed() {
        return severity.failed();
    }
}
