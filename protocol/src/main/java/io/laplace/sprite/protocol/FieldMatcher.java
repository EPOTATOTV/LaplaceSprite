package io.laplace.sprite.protocol;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * 字段断言匹配器（15.6）：把「期望」和「怎么比」封在一起。
 */
public final class FieldMatcher {

    private final Predicate<Object> predicate;
    private final String expectation;

    private FieldMatcher(Predicate<Object> predicate, String expectation) {
        this.predicate = predicate;
        this.expectation = expectation;
    }

    /** 自定义匹配器。 */
    public static FieldMatcher of(Predicate<Object> predicate, String expectation) {
        return new FieldMatcher(predicate, expectation);
    }

    /**
     * 相等。两侧都是数值时按数值比较：JSON 里的数字可能被解析成 Integer 或 Double，
     * 用 {@code Objects.equals} 会把 {@code 0} 与 {@code 0.0} 判成不等。
     */
    public static FieldMatcher eq(Object expected) {
        return new FieldMatcher(actual -> equalsValue(expected, actual), "等于 " + expected);
    }

    /** 字符串包含。 */
    public static FieldMatcher contains(String substring) {
        return new FieldMatcher(actual -> actual != null && String.valueOf(actual).contains(substring),
                "包含 " + substring);
    }

    /** 数值落在闭区间内；非数值或缺失一律判不通过。 */
    public static FieldMatcher inRange(double min, double max) {
        return new FieldMatcher(actual -> {
            Double value = asDouble(actual);
            return value != null && value >= min && value <= max;
        }, "在 [%s, %s] 内".formatted(min, max));
    }

    public boolean matches(Object actual) {
        return predicate.test(actual);
    }

    public String expectation() {
        return expectation;
    }

    private static Double asDouble(Object actual) {
        if (actual instanceof Number number) {
            return number.doubleValue();
        }
        if (actual instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private static boolean equalsValue(Object expected, Object actual) {
        if (expected instanceof Number || actual instanceof Number) {
            Double left = asDouble(expected);
            Double right = asDouble(actual);
            if (left != null && right != null) {
                return Double.compare(left, right) == 0;
            }
        }
        return Objects.equals(expected, actual);
    }
}
