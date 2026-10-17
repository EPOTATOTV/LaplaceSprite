package io.laplace.sprite.core.engine.replay;

/**
 * 回放断点（4.6 可断点）。
 *
 * <p>断点按 tick 把事件流切成多段；{@code predicate} 是可选的谓词名或表达式，只作为断点条件描述，
 * 由调用方（命令行 / 界面）解释，本类不执行它。</p>
 *
 * @param tick      断点所在 tick
 * @param predicate 可选谓词名 / 表达式
 * @param label     断点标签，用作分段名
 */
public record Breakpoint(long tick, String predicate, String label) {

    public Breakpoint {
        if (tick < 0) {
            throw new IllegalArgumentException("断点 tick 不能为负数: " + tick);
        }
    }

    public static Breakpoint at(long tick) {
        return new Breakpoint(tick, null, "tick-" + tick);
    }

    public static Breakpoint at(long tick, String label) {
        return new Breakpoint(tick, null, label);
    }

    public static Breakpoint when(long tick, String predicate, String label) {
        return new Breakpoint(tick, predicate, label);
    }

    public boolean hasPredicate() {
        return predicate != null && !predicate.isBlank();
    }
}
