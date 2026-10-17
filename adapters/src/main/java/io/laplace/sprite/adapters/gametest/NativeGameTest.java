package io.laplace.sprite.adapters.gametest;

import java.util.Objects;

/**
 * 原生 GameTest 声明里的一条用例（14.6 platform → native）。
 *
 * @param name      用例名
 * @param assertion 断言表达式（平台脚本里的期望）
 * @param failed    是否失败
 */
public record NativeGameTest(String name, String assertion, boolean failed) {

    public NativeGameTest {
        Objects.requireNonNull(name, "name");
        assertion = assertion == null ? "" : assertion;
    }
}
