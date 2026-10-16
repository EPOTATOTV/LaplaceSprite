package io.laplace.sprite.sandbox;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 世界快照（15.4）：用例开始前对 /data 取基线快照，结束后回灌，保证用例间零污染。
 *
 * <p>快照用目录树复制而不是 tar，省掉外部 tar 依赖，本地目录就能测；语义与 tar 快照一致
 * （先整体清空再回灌，避免残留文件把下一个用例带偏）。</p>
 */
public final class WorldSnapshotStore {

    public static final String BASELINE_DIR = "baseline";
    public static final String SERVER_PROPERTIES = "server.properties";

    private final Path workspace;

    public WorldSnapshotStore(Path workspace) {
        if (workspace == null) {
            throw new IllegalArgumentException("快照工作目录不能为空");
        }
        this.workspace = workspace;
    }

    public Path workspace() {
        return workspace;
    }

    public Path baselineDirectory(String caseId) {
        requireCaseId(caseId);
        return workspace.resolve(caseId).resolve(BASELINE_DIR);
    }

    public boolean hasBaseline(String caseId) {
        return Files.isDirectory(baselineDirectory(caseId));
    }

    /** 取基线快照；dataDir 不存在时落一个空基线，语义是「启动前就是空的」。 */
    public Path snapshotBaseline(String caseId, Path dataDir) {
        Path baseline = baselineDirectory(caseId);
        deleteRecursively(baseline);
        copyTree(dataDir, baseline);
        return baseline;
    }

    /** 回灌基线；没有基线时返回 false，调用方据此把 worldRestored 记为假。 */
    public boolean restoreBaseline(String caseId, Path dataDir) {
        Path baseline = baselineDirectory(caseId);
        if (!Files.isDirectory(baseline)) {
            return false;
        }
        deleteRecursively(dataDir);
        copyTree(baseline, dataDir);
        return true;
    }

    /** 全新 superflat 世界：清空 /data 并写一份最小 server.properties。 */
    public Path freshSuperflat(String caseId, Path dataDir) {
        requireCaseId(caseId);
        deleteRecursively(dataDir);
        try {
            Files.createDirectories(dataDir);
            Files.writeString(dataDir.resolve(SERVER_PROPERTIES), superflatProperties(),
                    StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("写入 superflat 世界配置失败: " + dataDir, ex);
        }
        return dataDir;
    }

    private static String superflatProperties() {
        return """
                level-name=world
                level-type=minecraft:flat
                generate-structures=false
                spawn-monsters=false
                gamemode=creative
                online-mode=false
                """;
    }

    private static void copyTree(Path source, Path target) {
        try {
            if (!Files.isDirectory(source)) {
                Files.createDirectories(target);
                return;
            }
            try (Stream<Path> paths = Files.walk(source)) {
                List<Path> entries = paths.toList();
                for (Path entry : entries) {
                    Path destination = target.resolve(source.relativize(entry).toString());
                    if (Files.isDirectory(entry)) {
                        Files.createDirectories(destination);
                    } else {
                        if (destination.getParent() != null) {
                            Files.createDirectories(destination.getParent());
                        }
                        Files.copy(entry, destination, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("复制目录树失败: " + source + " -> " + target, ex);
        }
    }

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path entry : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(entry);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("清空目录失败: " + root, ex);
        }
    }

    private static void requireCaseId(String caseId) {
        if (caseId == null || caseId.isBlank()) {
            throw new IllegalArgumentException("用例 id 不能为空");
        }
    }
}
