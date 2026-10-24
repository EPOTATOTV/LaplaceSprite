package io.laplace.sprite.game;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 版本缓存：{@code <root>/versions/<id>/} 下放 client.jar、server.jar、version.json、metadata.json。
 *
 * <p>版本 id 只允许 {@code [A-Za-z0-9._-]+}，且解析后的路径必须仍在 versions 目录内。</p>
 */
public final class GameCache {

    public static final String CLIENT_JAR = "client.jar";
    public static final String SERVER_JAR = "server.jar";
    public static final String VERSION_JSON = "version.json";
    public static final String METADATA_JSON = "metadata.json";

    private static final Pattern VERSION_ID = Pattern.compile("[A-Za-z0-9._-]+");

    private final Path root;

    public GameCache(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    public Path versionsDir() {
        return root.resolve("versions");
    }

    public Path versionDir(String versionId) {
        return resolve(versionsDir(), versionId);
    }

    public Path clientJar(String versionId) {
        return versionDir(versionId).resolve(CLIENT_JAR);
    }

    public Path serverJar(String versionId) {
        return versionDir(versionId).resolve(SERVER_JAR);
    }

    public Path versionFile(String versionId) {
        return versionDir(versionId).resolve(VERSION_JSON);
    }

    public Path metadataFile(String versionId) {
        return versionDir(versionId).resolve(METADATA_JSON);
    }

    /** 可安装的最小条件：version.json 与 client.jar 都在。 */
    public boolean isCached(String versionId) {
        return Files.isRegularFile(versionFile(versionId))
                && Files.isRegularFile(clientJar(versionId));
    }

    public void clean(String versionId) throws IOException {
        deleteRecursively(versionDir(versionId));
    }

    public void cleanAll() throws IOException {
        deleteRecursively(versionsDir());
    }

    private static Path resolve(Path parent, String versionId) {
        Path normalizedParent = parent.toAbsolutePath().normalize();
        if (versionId == null || !VERSION_ID.matcher(versionId).matches()
                || ".".equals(versionId) || "..".equals(versionId)) {
            throw new IllegalArgumentException("非法版本 id: " + versionId);
        }
        Path target = normalizedParent.resolve(versionId).normalize();
        if (!target.startsWith(normalizedParent)) {
            throw new IllegalArgumentException("版本 id 逃出缓存目录: " + versionId);
        }
        return target;
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }
}
