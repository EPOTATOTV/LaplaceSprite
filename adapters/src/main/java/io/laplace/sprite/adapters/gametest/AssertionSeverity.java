package io.laplace.sprite.adapters.gametest;

/**
 * 平台断言脚本里的结论等级（14.6）。
 *
 * <p>原生 GameTest 只有通过 / 失败两态，{@code WARN} 留给「日志里出现了用例但没有结论」
 * 这类无法判定通过与否的情况。</p>
 */
public enum AssertionSeverity {

    /** 通过。 */
    PASS,
    /** 无法判定结论，按通过处理但保留标记。 */
    WARN,
    /** 失败。 */
    FAIL;

    public boolean failed() {
        return this == FAIL;
    }

    public static AssertionSeverity of(boolean passed) {
        return passed ? PASS : FAIL;
    }
}
