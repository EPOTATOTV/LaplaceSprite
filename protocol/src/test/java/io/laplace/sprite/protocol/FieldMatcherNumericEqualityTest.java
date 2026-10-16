package io.laplace.sprite.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FieldMatcher#eq} 的数值比较回归：JSON 数字可能被解析成 Integer 或 Double，
 * 走过一次「CHUNK_DATA.chunkX 期望 0 实际 0 却判不通过」的真实误判。
 */
class FieldMatcherNumericEqualityTest {

    @Test
    void integerExpectationMatchesDoubleActual() {
        assertTrue(FieldMatcher.eq(0).matches(0.0d));
        assertTrue(FieldMatcher.eq(42).matches(42.0d));
    }

    @Test
    void doubleExpectationMatchesIntegerActual() {
        assertTrue(FieldMatcher.eq(1.0d).matches(1));
    }

    @Test
    void numericStringsStillCompare() {
        assertTrue(FieldMatcher.eq(15).matches("15"));
    }

    @Test
    void genuinelyDifferentNumbersDoNotMatch() {
        assertFalse(FieldMatcher.eq(0).matches(1.0d));
        assertFalse(FieldMatcher.eq(42).matches(41));
    }

    @Test
    void nonNumericEqualityIsUnchanged() {
        assertTrue(FieldMatcher.eq("CHAT").matches("CHAT"));
        assertFalse(FieldMatcher.eq("CHAT").matches("SYSTEM"));
    }
}
