package io.laplace.sprite.adapters;

import io.laplace.sprite.adapters.matrix.LegacyLoaderAppendix;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 版本矩阵附录 · 遗留加载器结论（14.3）。 */
class LegacyLoaderAppendixTest {

    @Test
    void riftAndLiteLoaderAreMarkedUnsupported() {
        List<LegacyLoaderAppendix.LegacyLoader> entries = LegacyLoaderAppendix.entries();
        assertEquals(2, entries.size());
        assertTrue(entries.stream().anyMatch(entry -> entry.loader().equals("Rift")
                && entry.versionRange().equals("1.13.x") && !entry.supported()));
        assertTrue(entries.stream().anyMatch(entry -> entry.loader().equals("LiteLoader")
                && entry.versionRange().equals("≤1.12.2") && !entry.supported()));
        assertEquals(2, LegacyLoaderAppendix.unsupported().size());
        assertTrue(LegacyLoaderAppendix.unsupported().stream()
                .allMatch(entry -> entry.reason().contains("客户端专属")));
    }

    @Test
    void forge1212IsTheHistoricalFallback() {
        assertTrue(LegacyLoaderAppendix.historicalFallback().contains("Forge 1.12.2"));
        assertFalse(LegacyLoaderAppendix.historicalFallback().isBlank());
    }
}
