package io.laplace.sprite.adapters.ecosystem;

import java.util.Objects;

/**
 * 生态兼容冒烟的一条断言（14.9）。
 *
 * @param id        断言标识，报告与健康度写回按它聚合
 * @param statement 断言内容
 */
public record BundleAssertion(String id, String statement) {

    public BundleAssertion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(statement, "statement");
    }
}
