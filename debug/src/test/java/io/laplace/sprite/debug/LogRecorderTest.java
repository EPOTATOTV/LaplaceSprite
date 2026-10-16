package io.laplace.sprite.debug;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 结构化日志录制（15.1 一期）：按线程切分，栈帧续行要跟着上一条走。 */
class LogRecorderTest {

    private static final String LATEST_LOG = """
            [12:00:00] [Server thread/INFO]: Starting minecraft server version 1.21.11
            [12:00:00] [Render thread/INFO]: Reloading resource manager
            [12:00:01] [Server thread/INFO]: Done (3.1s)! For help, type "help"
            \tat net.minecraft.server.MinecraftServer.run(MinecraftServer.java:1)
            [12:00:02] [Render thread/INFO]: Render thread finished
            """;

    @Test
    void splitsByThreadName(@TempDir Path tempDir) throws IOException {
        Path latest = writeLog(tempDir);
        LogRecorder recorder = new LogRecorder(tempDir.resolve("recordings"), true);

        List<RecordingArtifact> artifacts = recorder.record(latest);

        assertEquals(2, artifacts.size());
        RecordingArtifact server = artifactOf(artifacts, "Server thread");
        RecordingArtifact render = artifactOf(artifacts, "Render thread");
        assertEquals(3, server.lineCount(), "栈帧续行归上一条所属线程");
        assertEquals(2, render.lineCount());
        assertTrue(Files.readString(server.path()).contains("Done (3.1s)"));
        assertTrue(Files.readString(server.path()).contains("MinecraftServer.run"));
        assertTrue(Files.readString(render.path()).contains("Reloading resource manager"));
        assertEquals("log-Server-thread", server.id());
        assertTrue(recorder.splitByThread());
        assertEquals(tempDir.resolve("recordings"), recorder.outputDirectory());
    }

    @Test
    void mergedModeProducesOneArtifact(@TempDir Path tempDir) throws IOException {
        LogRecorder recorder = new LogRecorder(tempDir.resolve("recordings"), false);

        List<RecordingArtifact> artifacts = recorder.record(writeLog(tempDir));

        assertEquals(1, artifacts.size());
        assertEquals("all", artifacts.get(0).threadName());
        assertEquals(5, artifacts.get(0).lineCount());
        assertFalse(recorder.splitByThread());
    }

    @Test
    void linesWithoutThreadPrefixGoToUnknown(@TempDir Path tempDir) throws IOException {
        Path latest = tempDir.resolve("latest.log");
        Files.writeString(latest, "raw line without thread prefix" + System.lineSeparator());

        List<RecordingArtifact> artifacts = new LogRecorder(tempDir.resolve("recordings"), true).record(latest);

        assertEquals(1, artifacts.size());
        assertEquals(LogRecorder.UNKNOWN_THREAD, artifacts.get(0).threadName());
    }

    @Test
    void missingLogAndBlankOutputDirAreReported(@TempDir Path tempDir) {
        assertThrows(UncheckedIOException.class,
                () -> new LogRecorder(tempDir, true).record(tempDir.resolve("missing.log")));
        assertThrows(IllegalArgumentException.class, () -> new LogRecorder(null, true));
    }

    private static Path writeLog(Path tempDir) throws IOException {
        Path latest = tempDir.resolve("latest.log");
        Files.writeString(latest, LATEST_LOG);
        return latest;
    }

    private static RecordingArtifact artifactOf(List<RecordingArtifact> artifacts, String threadName) {
        return artifacts.stream().filter(artifact -> artifact.threadName().equals(threadName)).findFirst()
                .orElseThrow();
    }
}
