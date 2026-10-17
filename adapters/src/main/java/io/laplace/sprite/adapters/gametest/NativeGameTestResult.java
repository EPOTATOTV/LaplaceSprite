package io.laplace.sprite.adapters.gametest;

import java.util.List;

/**
 * 原生 GameTest 一次的运行结果（14.6）。
 *
 * <p>语义对齐 PackTest / mc-runtime-test：{@code /test runall} 的退出码就是失败用例数，
 * 所以 {@code exitCode == failed} 是硬约束，构造时直接校验，避免上游把两者写岔。</p>
 *
 * @param total    用例总数（日志里的通过 + 失败）
 * @param failed   失败用例数
 * @param exitCode 进程退出码
 * @param cases    逐条用例结果
 */
public record NativeGameTestResult(int total, int failed, int exitCode, List<GameTestCaseResult> cases) {

    public NativeGameTestResult {
        if (total < 0 || failed < 0) {
            throw new IllegalArgumentException("用例计数不能为负：total=%d, failed=%d".formatted(total, failed));
        }
        if (exitCode != failed) {
            throw new IllegalArgumentException(
                    "退出码必须等于失败用例数：exitCode=%d, failed=%d".formatted(exitCode, failed));
        }
        cases = cases == null ? List.of() : List.copyOf(cases);
    }

    public boolean passed() {
        return failed == 0;
    }
}
