package io.laplace.sprite.debug;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mixin 织入快照（15.1）：导出参数、扫描统计、打包成证据。 */
class MixinExportCollectorTest {

    @Test
    void devDefaultsTurnOnExportDecompileAndDirectory() {
        List<String> args = MixinExportOptions.devDefault().jvmArguments();

        assertTrue(args.contains("-Dmixin.debug.export=true"));
        assertTrue(args.contains("-Dmixin.debug.export.dir=.mixin.out"));
        assertTrue(args.contains("-Dmixin.debug.export.decompile=true"));
        assertTrue(MixinExportOptions.devDefault().exportPath(Path.of("run")).endsWith(".mixin.out"));
        assertTrue(MixinExportOptions.disabled().jvmArguments().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new MixinExportOptions(true, " ", true));
    }

    @Test
    void collectorCountsClassesAndSourcesThenBundlesZip(@TempDir Path tempDir) throws IOException {
        Path instanceRoot = tempDir.resolve("run");
        Path exportDir = instanceRoot.resolve(MixinExportOptions.DEFAULT_EXPORT_DIR);
        Files.createDirectories(exportDir.resolve("net/example"));
        Files.writeString(exportDir.resolve("net/example/Target.class"), "bytecode");
        Files.writeString(exportDir.resolve("net/example/Target.java"), "source");
        Files.writeString(exportDir.resolve("net/example/TargetMixin.class"), "bytecode");

        MixinExportCollector collector = new MixinExportCollector(instanceRoot, MixinExportOptions.devDefault());
        MixinExportSnapshot snapshot = collector.snapshot("debug-1");

        assertEquals(2, snapshot.exportedClassCount());
        assertEquals(1, snapshot.decompiledSourceCount());
        assertEquals(List.of("net.example.Target", "net.example.TargetMixin"), snapshot.targets());
        assertFalse(snapshot.empty());
        assertEquals(exportDir, collector.exportDir());

        Path zip = collector.bundleZip("debug-1", tempDir.resolve("evidence/mixin-out.zip"));
        assertTrue(Files.isRegularFile(zip));
        try (ZipFile zipFile = new ZipFile(zip.toFile())) {
            assertEquals(3, zipFile.size());
            assertNotNull(zipFile.getEntry("net/example/Target.class"));
            assertNotNull(zipFile.getEntry("net/example/Target.java"));
        }
    }

    @Test
    void missingExportDirYieldsEmptySnapshot(@TempDir Path tempDir) {
        MixinExportCollector collector = new MixinExportCollector(tempDir, MixinExportOptions.devDefault());
        MixinExportSnapshot snapshot = collector.snapshot("debug-2");

        assertTrue(snapshot.empty());
        assertTrue(snapshot.targets().isEmpty());
        assertTrue(Files.isRegularFile(collector.bundleZip("debug-2", tempDir.resolve("empty.zip"))));
    }
}
