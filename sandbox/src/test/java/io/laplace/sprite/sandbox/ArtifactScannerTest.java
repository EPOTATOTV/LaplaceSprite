package io.laplace.sprite.sandbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 制品扫描（L3-04）：正常包放行，逃逸 / 体积 / 可执行头 / 可疑特征都不干净。 */
class ArtifactScannerTest {

    private final ArtifactScanner scanner = new ArtifactScanner();

    @TempDir
    Path dir;

    @Test
    void ordinaryJarPasses() throws IOException {
        Path jar = zip("normal.jar", Map.of(
                "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\n".getBytes(StandardCharsets.UTF_8),
                "META-INF/services/io.laplace.sprite.core.spi.AdapterProvider",
                "demo.Provider".getBytes(StandardCharsets.UTF_8),
                "fabric.mod.json", "{\"id\":\"demo\"}".getBytes(StandardCharsets.UTF_8),
                "assets/demo/icon.png", new byte[]{1, 2, 3, 4},
                "com/example/Demo.class", "not-a-real-class".getBytes(StandardCharsets.UTF_8)));

        ArtifactScanner.ScanResult result = scanner.scan(jar);
        assertTrue(result.clean(), String.join("；", result.findings()));
        assertEquals(5, result.entries());
        assertTrue(result.uncompressedBytes() > 0);
    }

    @Test
    void pathTraversalEntryIsRejected() throws IOException {
        ArtifactScanner.ScanResult result = scan("../../evil.class", "x");
        assertFalse(result.clean());
        assertTrue(result.findings().stream().anyMatch(f -> f.contains("..")), result.findings().toString());
    }

    @Test
    void absoluteAndBackslashEntriesAreRejected() throws IOException {
        ArtifactScanner.ScanResult absolute = scan("/etc/passwd", "x");
        assertFalse(absolute.clean());
        assertTrue(absolute.findings().stream().anyMatch(f -> f.contains("绝对路径")));

        ArtifactScanner.ScanResult backslash = scan("..\\escape.class", "x");
        assertFalse(backslash.clean());
        assertTrue(backslash.findings().stream().anyMatch(f -> f.contains("反斜杠")));
    }

    @Test
    void disallowedSuffixIsRejected() throws IOException {
        ArtifactScanner.ScanResult result = scan("run.sh", "echo hi");
        assertFalse(result.clean());
        assertTrue(result.findings().stream().anyMatch(f -> f.contains("后缀")), result.findings().toString());
    }

    @Test
    void executableAndShebangHeadersAreRejected() throws IOException {
        ArtifactScanner.ScanResult mz = scan("payload.class", "MZ\u0000\u0000program");
        assertFalse(mz.clean());
        assertTrue(mz.findings().stream().anyMatch(f -> f.contains("MZ")));

        ArtifactScanner.ScanResult shebang = scan("run.txt", "#!/bin/sh\nrm -rf /");
        assertFalse(shebang.clean());
        assertTrue(shebang.findings().stream().anyMatch(f -> f.contains("shebang")));
    }

    @Test
    void suspiciousApiAndLongBase64AreFindings() throws IOException {
        ArtifactScanner.ScanResult exec = scan("com/example/Evil.class",
                "Runtime.getRuntime().exec(\"calc\")");
        assertFalse(exec.clean());
        assertTrue(exec.findings().stream().anyMatch(f -> f.contains("runtime.getruntime().exec")));

        String base64 = "A".repeat(600);
        ArtifactScanner.ScanResult encoded = scan("blob.txt", base64);
        assertFalse(encoded.clean());
        assertTrue(encoded.findings().stream().anyMatch(f -> f.contains("base64")),
                encoded.findings().toString());
    }

    @Test
    void tooManyEntriesIsRejected() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (int i = 0; i <= ArtifactScanner.MAX_ENTRIES; i++) {
            entries.put("e" + i + ".txt", new byte[0]);
        }
        Path jar = zip("many.zip", entries);

        ArtifactScanner.ScanResult result = scanner.scan(jar);
        assertFalse(result.clean());
        assertTrue(result.findings().stream().anyMatch(f -> f.contains("条目数超过阈值")),
                result.findings().toString());
    }

    private ArtifactScanner.ScanResult scan(String name, String content) throws IOException {
        return scanner.scan(zip("case.zip", Map.of(name, content.getBytes(StandardCharsets.UTF_8))));
    }

    private Path zip(String fileName, Map<String, byte[]> entries) throws IOException {
        Path target = dir.resolve(fileName);
        try (OutputStream raw = Files.newOutputStream(target);
             ZipOutputStream zip = new ZipOutputStream(raw, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return target;
    }
}
