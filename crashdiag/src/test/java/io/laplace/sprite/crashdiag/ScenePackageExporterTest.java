package io.laplace.sprite.crashdiag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 问题现场包导出（15.5）：zip 里该有的条目、清单都要能对上。 */
class ScenePackageExporterTest {

    private static final String OOM_LOG = """
            java.lang.OutOfMemoryError: Java heap space
            \tat com.example.perfcore.Cache.grow(Cache.java:77)
            \tat com.example.perfcore.Cache.put(Cache.java:31)
            """;

    @TempDir
    Path tempDir;

    @Test
    void exportsScenePackageWithManifest() throws IOException {
        Path instance = tempDir.resolve("instance");
        Files.createDirectories(instance.resolve("crash-reports"));
        Files.createDirectories(instance.resolve("logs"));
        Files.writeString(instance.resolve("crash-reports").resolve("crash-1.txt"), "crash body");
        Files.writeString(instance.resolve("logs").resolve("latest.log"), "log body");
        Files.writeString(instance.resolve("hs_err_pid999.log"), "SIGSEGV at 0x0");
        Files.writeString(instance.resolve("start-command.txt"), "java -jar server.jar");
        Files.writeString(instance.resolve("jvm-args.txt"), "-Xmx4G");
        Files.writeString(instance.resolve("mod-manifest.txt"), "fabric-api 0.92.0");

        CrashArtifacts artifacts = new CrashCollector().collect(instance);
        CrashDiagnosis diagnosis = new CrashDiagnoser().diagnose("inst-scene", artifacts, OOM_LOG);

        Path zip = new ScenePackageExporter()
                .export(tempDir.resolve("out").resolve("scene.zip"), artifacts, diagnosis);
        assertTrue(Files.isRegularFile(zip));

        Map<String, String> entries = readEntries(zip);
        assertTrue(entries.containsKey("crash-reports/crash-1.txt"));
        assertTrue(entries.containsKey("logs/latest.log"));
        assertTrue(entries.containsKey("hs_err/hs_err_pid999.log"));
        assertTrue(entries.containsKey("env/start-command.txt"));
        assertTrue(entries.containsKey("env/jvm-args.txt"));
        assertTrue(entries.containsKey("env/mod-manifest.txt"));
        assertTrue(entries.containsKey(ScenePackageExporter.DIAGNOSIS_ENTRY));
        assertTrue(entries.containsKey(ScenePackageExporter.MANIFEST_ENTRY));
        assertTrue(entries.get("logs/latest.log").contains("log body"));
        assertTrue(entries.get("crash-reports/crash-1.txt").contains("crash body"));

        String manifest = entries.get(ScenePackageExporter.MANIFEST_ENTRY);
        assertTrue(manifest.contains("instanceId=inst-scene"));
        assertTrue(manifest.contains("diagnosisId=" + diagnosis.id()));
        assertTrue(manifest.contains("category=" + diagnosis.category().name()));
        assertTrue(manifest.contains("collectedAt="));
        assertTrue(manifest.contains("kind=crash-reports"));
        assertTrue(manifest.contains("latest.log"));
        assertTrue(manifest.contains("entry=" + ScenePackageExporter.DIAGNOSIS_ENTRY));
        assertTrue(manifest.contains("entry=" + ScenePackageExporter.MANIFEST_ENTRY));

        assertTrue(entries.get(ScenePackageExporter.DIAGNOSIS_ENTRY).contains(diagnosis.category().name()));
        assertEquals(8, entries.size(), "六份现场 + 诊断 + 清单");
    }

    @Test
    void exportsZipEvenWhenNothingWasCollected() throws IOException {
        Path instance = Files.createTempDirectory("crash-empty");
        CrashArtifacts artifacts = new CrashCollector().collect(instance);
        CrashDiagnosis diagnosis = new CrashDiagnoser().diagnose("inst-empty", artifacts, OOM_LOG);

        Path zip = new ScenePackageExporter().export(tempDir.resolve("empty.zip"), artifacts, diagnosis);
        Map<String, String> entries = readEntries(zip);

        assertEquals(2, entries.size(), "没有现场时也至少有诊断与清单");
        assertTrue(entries.containsKey(ScenePackageExporter.DIAGNOSIS_ENTRY));
        assertTrue(entries.get(ScenePackageExporter.MANIFEST_ENTRY).contains("instanceId=inst-empty"));
    }

    private static Map<String, String> readEntries(Path zip) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (InputStream raw = Files.newInputStream(zip); ZipInputStream in = new ZipInputStream(raw)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }
}
