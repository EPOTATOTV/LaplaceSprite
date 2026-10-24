package io.laplace.sprite.game;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 缓存目录布局、清理与路径逃逸防护。 */
class GameCacheTest {

    @TempDir
    Path temp;

    private GameCache cache;

    @BeforeEach
    void setUp() {
        cache = new GameCache(temp.resolve("game"));
    }

    @Test
    void tracksAndCleansSingleVersion() throws IOException {
        seed("26.3");
        seed("26.2");

        assertTrue(cache.isCached("26.3"));
        assertTrue(Files.isRegularFile(cache.clientJar("26.3")));
        assertTrue(Files.isRegularFile(cache.serverJar("26.3")));
        assertTrue(Files.isRegularFile(cache.versionFile("26.3")));
        assertTrue(Files.isRegularFile(cache.metadataFile("26.3")));

        cache.clean("26.3");

        assertFalse(Files.exists(cache.versionDir("26.3")));
        assertTrue(cache.isCached("26.2"), "清理单版本不能影响其他版本");

        cache.clean("26.3");
        assertFalse(Files.exists(cache.versionDir("26.3")), "重复清理应为无操作");
    }

    @Test
    void cleansAllVersions() throws IOException {
        seed("26.3");
        seed("1.21.11");

        cache.cleanAll();

        assertFalse(Files.exists(cache.versionsDir()));
        assertFalse(cache.isCached("26.3"));
        assertFalse(cache.isCached("1.21.11"));
    }

    @Test
    void rejectsPathEscapes() {
        assertThrows(IllegalArgumentException.class, () -> cache.clientJar("../escape"));
        assertThrows(IllegalArgumentException.class, () -> cache.versionFile("a/b"));
        assertThrows(IllegalArgumentException.class, () -> cache.metadataFile(".."));
        assertThrows(IllegalArgumentException.class, () -> cache.versionDir("a\\b"));
        assertThrows(IllegalArgumentException.class, () -> cache.versionDir(""));
        assertThrows(IllegalArgumentException.class, () -> cache.clean("C:\\Windows"));
    }

    private void seed(String versionId) throws IOException {
        Files.createDirectories(cache.versionDir(versionId));
        Files.write(cache.clientJar(versionId), new byte[]{1, 2, 3});
        Files.write(cache.serverJar(versionId), new byte[]{4, 5, 6});
        Files.writeString(cache.versionFile(versionId), "{}", StandardCharsets.UTF_8);
        Files.writeString(cache.metadataFile(versionId), "{}", StandardCharsets.UTF_8);
    }
}
