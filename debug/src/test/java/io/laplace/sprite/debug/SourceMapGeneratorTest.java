package io.laplace.sprite.debug;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 源码映射（15.1）：非 Loom 工程与 gradle 缺失都要降级，且降级理由写清楚。 */
class SourceMapGeneratorTest {

    @Test
    void nonLoomProjectDegradesWithExplanation(@TempDir Path tempDir) {
        SourceMapGenerator generator = new SourceMapGenerator("definitely-not-a-gradle-binary-xyz");
        SourceMapReport report = generator.generate(SourceMapRequest.of("debug-1", tempDir));

        assertFalse(report.generated());
        assertNull(report.sourceDir());
        assertFalse(report.notes().isEmpty());
        assertTrue(report.notes().get(0).contains("fabric-loom"), "降级原因要指名 Loom");
        assertEquals("debug-1", report.instanceId());
    }

    @Test
    void loomProjectWithMissingGradleDegradesWithoutPretending(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("build.gradle"), "plugins { id 'fabric-loom' version '1.9' }");
        SourceMapGenerator generator = new SourceMapGenerator("definitely-not-a-gradle-binary-xyz");

        assertTrue(generator.isLoomProject(tempDir));
        SourceMapReport report = generator.generate(new SourceMapRequest("debug-2", tempDir,
                tempDir.resolve("mc-sources"), "genSources"));

        assertFalse(report.generated(), "gradle 不在就不得声称生成成功");
        assertNull(report.sourceDir());
        assertTrue(report.notes().get(0).contains("gradle"), "降级原因要指名 gradle");
    }

    @Test
    void kotlinDslBuildScriptIsRecognized(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("build.gradle.kts"), "plugins { id(\"fabric-loom\") }");
        assertTrue(new SourceMapGenerator().isLoomProject(tempDir));
    }

    @Test
    void invalidInputsAreRejected(@TempDir Path tempDir) {
        SourceMapGenerator generator = new SourceMapGenerator();
        assertFalse(generator.isLoomProject(tempDir.resolve("missing")));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new SourceMapGenerator(" "));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new SourceMapRequest(" ", tempDir, tempDir, "genSources"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new SourceMapRequest("i", tempDir, tempDir, " "));
    }
}
