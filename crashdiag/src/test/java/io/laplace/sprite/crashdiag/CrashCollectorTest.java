package io.laplace.sprite.crashdiag;

import io.laplace.sprite.crashdiag.CrashArtifacts.CollectedFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 现场采集（15.5）：按约定目录布局收集四类现场，缺什么也要留痕。 */
class CrashCollectorTest {

    private final CrashCollector collector = new CrashCollector();

    private Path instanceDir;

    @BeforeEach
    void setUp() throws IOException {
        instanceDir = Files.createTempDirectory("crash-instance");
        Files.createDirectories(instanceDir.resolve("crash-reports"));
        Files.createDirectories(instanceDir.resolve("logs"));
        Files.writeString(instanceDir.resolve("crash-reports").resolve("crash-2026-01-01_00.00.00-server.txt"),
                "crash body");
        Files.writeString(instanceDir.resolve("logs").resolve("latest.log"), "log body");
        Files.writeString(instanceDir.resolve("start-command.txt"), "java -jar server.jar");
        Files.writeString(instanceDir.resolve("jvm-args.txt"), "-Xmx4G");
        Files.writeString(instanceDir.resolve("mod-manifest.txt"), "fabric-api 0.92.0");
    }

    @Test
    void collectsConventionalLayout() {
        CrashArtifacts artifacts = collector.collect(instanceDir);

        assertEquals(instanceDir.toAbsolutePath().normalize(), artifacts.root());
        assertTrue(artifacts.hasKind(CrashArtifacts.KIND_CRASH_REPORTS));
        assertTrue(artifacts.hasKind(CrashArtifacts.KIND_LATEST_LOG));
        assertTrue(artifacts.hasKind(CrashArtifacts.KIND_START_COMMAND));
        assertTrue(artifacts.hasKind(CrashArtifacts.KIND_JVM_ARGS));
        assertTrue(artifacts.hasKind(CrashArtifacts.KIND_MOD_MANIFEST));
        assertFalse(artifacts.hsErrPresent());

        String text = collector.combinedText(artifacts);
        assertTrue(text.contains("crash body"));
        assertTrue(text.contains("log body"));
    }

    @Test
    void collectsHsErrFromRootAndLogsDirectory() throws IOException {
        Files.writeString(instanceDir.resolve("hs_err_pid1234.log"), "SIGSEGV at 0x0");
        Files.writeString(instanceDir.resolve("logs").resolve("hs_err_pid5678.log"), "SIGSEGV at 0x0");

        CrashArtifacts artifacts = collector.collect(instanceDir);

        assertTrue(artifacts.hsErrPresent());
        assertEquals(2, artifacts.ofKind(CrashArtifacts.KIND_HS_ERR).stream()
                .filter(CollectedFile::found).count());
        assertTrue(collector.combinedText(artifacts).contains("SIGSEGV"));
    }

    @Test
    void missingArtifactsAreRecordedAsNotFound() throws IOException {
        Path empty = Files.createTempDirectory("crash-empty");
        CrashArtifacts artifacts = collector.collect(empty);

        assertFalse(artifacts.hasKind(CrashArtifacts.KIND_CRASH_REPORTS));
        assertFalse(artifacts.hasKind(CrashArtifacts.KIND_LATEST_LOG));
        assertFalse(artifacts.hsErrPresent());
        assertTrue(artifacts.found().isEmpty());
        assertEquals(1, artifacts.ofKind(CrashArtifacts.KIND_HS_ERR).size());
        assertFalse(artifacts.ofKind(CrashArtifacts.KIND_HS_ERR).get(0).found());
        assertEquals("", collector.combinedText(artifacts));
    }
}
