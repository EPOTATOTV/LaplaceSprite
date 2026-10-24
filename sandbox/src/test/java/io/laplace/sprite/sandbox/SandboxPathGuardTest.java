package io.laplace.sprite.sandbox;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 沙箱路径闸门（L3-04）：根内放行、根外拒绝、敏感路径与符号链接逃逸都拦下。 */
class SandboxPathGuardTest {

    private final SandboxPathGuard guard = new SandboxPathGuard();

    @TempDir
    Path dir;

    @Test
    void readInsideAllowedRootPasses() throws IOException {
        Path root = Files.createDirectories(dir.resolve("instance"));
        Files.createDirectories(root.resolve("mods"));
        Path file = Files.writeString(root.resolve("mods/example.jar"), "jar");
        Path checked = guard.checkRead(file, List.of(root));

        assertEquals(file.toRealPath(), checked);
    }

    @Test
    void readOutsideAllowedRootIsRejected() throws IOException {
        Path root = Files.createDirectories(dir.resolve("instance"));
        Path outside = Files.writeString(dir.resolve("outside.txt"), "x");

        assertThrows(SecurityException.class, () -> guard.checkRead(outside, List.of(root)));
        assertThrows(SecurityException.class, () -> guard.checkRead(outside, List.of()));
    }

    @Test
    void writeInsideWritableRootPassesAndOutsideFails() throws IOException {
        Path root = Files.createDirectories(dir.resolve("instance"));
        Path target = root.resolve("world/level.dat");

        Path checked = guard.checkWrite(target, root);
        assertTrue(checked.startsWith(root.toRealPath()));
        assertThrows(SecurityException.class,
                () -> guard.checkWrite(dir.resolve("elsewhere.txt"), root));
    }

    @Test
    void relativeSegmentsAreNormalizedBeforeContainmentCheck() throws IOException {
        Path root = Files.createDirectories(dir.resolve("instance"));
        Files.createDirectories(root.resolve("mods"));

        Path checked = guard.checkRead(root.resolve("mods/../mods/new.txt"), List.of(root));
        assertEquals(root.toRealPath().resolve("mods").resolve("new.txt"), checked);
    }

    @Test
    void sensitivePathIsRejectedEvenInsideAllowedRoot() {
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        Path sensitive = windows ? Path.of("C:\\Windows\\System32") : Path.of("/etc/passwd");
        Path allowedRoot = windows ? Path.of("C:\\Windows") : Path.of("/");

        assertThrows(SecurityException.class,
                () -> guard.checkRead(sensitive, List.of(allowedRoot)));
    }

    @Test
    void symlinkEscapingRootIsRejected() throws IOException {
        Path root = Files.createDirectories(dir.resolve("instance"));
        Path outside = Files.writeString(dir.resolve("secret.txt"), "secret");
        Path link = root.resolve("linked.txt");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (IOException | UnsupportedOperationException ex) {
            Assumptions.assumeTrue(false, "当前环境不允许创建符号链接：" + ex.getMessage());
        }

        assertThrows(SecurityException.class, () -> guard.checkRead(link, List.of(root)));
    }
}
