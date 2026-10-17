package io.laplace.sprite.cheats.report;

import java.util.Locale;

/** 报表数值格式化。固定 {@link Locale#ROOT}，避免小数点、百分号随运行环境语言变化。 */
final class RateFormat {

    private RateFormat() {
    }

    static String percent(double rate) {
        return String.format(Locale.ROOT, "%.1f%%", rate * 100);
    }
}
