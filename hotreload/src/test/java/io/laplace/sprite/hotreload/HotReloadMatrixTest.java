package io.laplace.sprite.hotreload;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 重载能力自检矩阵（15.2）：全组合可查询，也能渲染成 Markdown 表。 */
class HotReloadMatrixTest {

    private final HotReloadMatrix matrix = new HotReloadMatrix();

    @Test
    void matrixCoversEveryCombination() {
        assertEquals(JvmKind.values().length, matrix.jvms().size());
        assertEquals(ChangeKind.values().length, matrix.changes().size());
        for (JvmKind jvm : JvmKind.values()) {
            for (ChangeKind kind : ChangeKind.values()) {
                assertNotNull(matrix.at(jvm, kind), jvm + " × " + kind + " 必须给结论");
            }
        }
        assertTrue(matrix.at(JvmKind.HOTSPOT, ChangeKind.METHOD_BODY).hotSwappable());
        assertFalse(matrix.at(JvmKind.HOTSPOT, ChangeKind.FIELD).hotSwappable());
        assertTrue(matrix.at(JvmKind.DCEVM, ChangeKind.FIELD).hotSwappable());
        assertTrue(matrix.at(JvmProbe.of(Map.of(), "OpenJDK 64-Bit Server VM (JBR 21)"),
                ChangeKind.METHOD_SIGNATURE).hotSwappable());
    }

    @Test
    void markdownCarriesHeaderAndEveryVerdict() {
        String markdown = matrix.toMarkdown();

        assertTrue(markdown.contains("| JVM \\ 改动类型 |"));
        for (JvmKind jvm : JvmKind.values()) {
            assertTrue(markdown.contains(jvm.displayName()), "缺列 " + jvm.displayName());
        }
        for (ChangeKind kind : ChangeKind.values()) {
            assertTrue(markdown.contains(kind.displayName()), "缺表头 " + kind.displayName());
        }
        assertTrue(markdown.contains("可热生效"));
        assertTrue(markdown.contains(HotReloadCapability.RESTART_REQUIRED));
        assertTrue(markdown.contains("sponge-mixin"));
    }

    @Test
    void singleJvmTableHidesOtherJvms() {
        String markdown = matrix.toMarkdown(JvmKind.HOTSPOT);

        assertTrue(markdown.contains(JvmKind.HOTSPOT.displayName()));
        assertFalse(markdown.contains(JvmKind.DCEVM.displayName()));
        assertTrue(markdown.contains(JvmKind.HOTSPOT.displayName() + " |"));
    }
}
