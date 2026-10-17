package io.laplace.sprite.adapters.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 原生风格的 GameTest 声明（14.6 platform → native）。
 *
 * <p>刻意不生成 Java 源码：各代 GameTest 的形态不同（1.21.5 后是 test_instance 注册表 + nbt 结构，
 * Fabric 仍是 {@code @GameTest} 注解 + gametest entrypoint），声明只承载「用例名 + 断言 + 结论」
 * 这三样可翻译字段，由适配器落到具体格式。</p>
 *
 * @param suite 套件名
 * @param tests 用例声明
 */
public record NativeGameTestDeclaration(String suite, List<NativeGameTest> tests) {

    public NativeGameTestDeclaration {
        Objects.requireNonNull(suite, "suite");
        tests = tests == null ? List.of() : List.copyOf(tests);
    }

    /**
     * 把声明视作一次原生运行的产物。
     *
     * <p>用于往返校验与事件发射：声明里的失败标志直接换算成失败计数与退出码。</p>
     */
    public NativeGameTestResult asResult() {
        List<GameTestCaseResult> cases = new ArrayList<>(tests.size());
        int failed = 0;
        for (NativeGameTest test : tests) {
            if (test.failed()) {
                failed++;
            }
            cases.add(new GameTestCaseResult(test.name(), !test.failed(), test.assertion()));
        }
        return new NativeGameTestResult(cases.size(), failed, failed, cases);
    }
}
