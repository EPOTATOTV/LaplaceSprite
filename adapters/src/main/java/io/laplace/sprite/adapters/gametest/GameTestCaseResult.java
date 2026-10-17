package io.laplace.sprite.adapters.gametest;

import java.util.Objects;

/**
 * 单条原生 GameTest 用例结果。
 *
 * @param name   用例全名（如 {@code test.laplace.legit.WalkBridgeTest}）
 * @param passed 是否通过
 * @param detail 失败消息；通过用例为空串（原生输出不打印通过的断言表达式）
 */
public record GameTestCaseResult(String name, boolean passed, String detail) {

    public GameTestCaseResult {
        Objects.requireNonNull(name, "name");
        detail = detail == null ? "" : detail;
    }
}
