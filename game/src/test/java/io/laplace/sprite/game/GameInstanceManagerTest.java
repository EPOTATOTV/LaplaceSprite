package io.laplace.sprite.game;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 实例复制、id 校验与缓存前置条件。 */
class GameInstanceManagerTest {

    @TempDir
    Path temp;

    private GameCache cache;
    private GameInstanceManager manager;

    @BeforeEach
    void setUp() throws IOException {
        cache = new GameCache(temp.resolve("game"));
        Files.createDirectories(cache.versionDir("26.3"));
        Files.writeString(cache.versionFile("26.3"), versionJson(), StandardCharsets.UTF_8);
        Files.write(cache.clientJar("26.3"), "jar-content".getBytes(StandardCharsets.UTF_8));
        Files.writeString(cache.metadataFile("26.3"), "{}", StandardCharsets.UTF_8);
        manager = new GameInstanceManager(cache);
    }

    @Test
    void twoInstancesOfSameVersionStayIndependent() throws IOException {
        GameInstance first = manager.create("26.3", "first");
        GameInstance second = manager.create("26.3", "second");

        assertNotEquals(first.clientJar(), second.clientJar());
        assertEquals("26.3", first.metadata().id());
        assertTrue(Files.isRegularFile(first.root().resolve(GameCache.VERSION_JSON)));
        assertTrue(Files.isRegularFile(first.root().resolve(GameCache.METADATA_JSON)));

        Files.writeString(first.clientJar(), "modified", StandardCharsets.UTF_8);
        assertEquals("jar-content", Files.readString(second.clientJar(), StandardCharsets.UTF_8),
                "修改一个实例不能影响另一个");

        List<GameInstance> instances = manager.list();
        assertEquals(2, instances.size());
        assertEquals("first", instances.get(0).instanceId());
        assertEquals("second", instances.get(1).instanceId());
    }

    @Test
    void rejectsIllegalInstanceIds() {
        assertThrows(IllegalArgumentException.class, () -> manager.create("26.3", "../escape"));
        assertThrows(IllegalArgumentException.class, () -> manager.create("26.3", "a/b"));
        assertThrows(IllegalArgumentException.class, () -> manager.create("26.3", ".."));
        assertThrows(IllegalArgumentException.class, () -> manager.create("26.3", ""));
    }

    @Test
    void rejectsCreationWhenCacheIsMissing() {
        assertThrows(IllegalStateException.class, () -> manager.create("1.21.11", "missing"));
    }

    @Test
    void rejectsDuplicateInstanceId() throws IOException {
        manager.create("26.3", "dup");
        assertThrows(IllegalArgumentException.class, () -> manager.create("26.3", "dup"));
    }

    private static String versionJson() {
        return """
                {
                  "id": "26.3",
                  "type": "release",
                  "releaseTime": "2026-09-15T12:00:00+00:00",
                  "mainClass": "net.minecraft.client.main.Main",
                  "assets": "34",
                  "downloads": {
                    "client": {
                      "url": "https://example.invalid/client.jar",
                      "sha1": "0123456789abcdef0123456789abcdef01234567",
                      "size": 10
                    }
                  }
                }
                """;
    }
}
