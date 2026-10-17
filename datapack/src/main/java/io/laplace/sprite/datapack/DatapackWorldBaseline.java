package io.laplace.sprite.datapack;

import io.laplace.sprite.sandbox.SandboxSpec;
import io.laplace.sprite.sandbox.WorldSnapshotStore;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 世界基线与 {@code --level} 接线（15.8）：复用沙箱轨的快照与世界目录，不另造一套。
 *
 * <p>流程是「回灌基线快照 → 把待测数据包放进 {@code <世界>/datapacks/} → 把 LEVEL 指向基线世界」。
 * itzg 镜像认 {@code LEVEL} 环境变量，本地进程轨认 {@code --level} 参数，两边都给出。</p>
 */
public final class DatapackWorldBaseline {

    public static final String LEVEL_NAME = "world";
    public static final String DATAPACKS_DIR = "datapacks";
    public static final String LEVEL_ARGUMENT = "--level";

    /**
     * 接线结果。
     *
     * @param spec           补上 LEVEL 环境变量后的沙箱定义
     * @param worldDir       容器内 {@code /data/world} 对应的宿主目录
     * @param datapacksDir   数据包落点
     * @param level          世界名
     * @param levelArguments 本地进程轨的启动参数
     * @param notes          附注
     */
    public record Prepared(SandboxSpec spec, Path worldDir, Path datapacksDir, String level,
                           List<String> levelArguments, List<String> notes) {

        public Prepared {
            levelArguments = levelArguments == null ? List.of() : List.copyOf(levelArguments);
            notes = notes == null ? List.of() : List.copyOf(notes);
        }
    }

    private final WorldSnapshotStore snapshots;

    public DatapackWorldBaseline(WorldSnapshotStore snapshots) {
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
    }

    /**
     * 回灌基线并安装数据包。
     *
     * @param spec           沙箱定义，writableDataDir 即容器 /data 的宿主目录
     * @param baselineCaseId 基线快照的用例 id
     * @param packSource     数据包目录或 zip
     */
    public Prepared prepare(SandboxSpec spec, String baselineCaseId, Path packSource) {
        List<String> notes = new ArrayList<>();
        boolean restored = snapshots.restoreBaseline(baselineCaseId, spec.writableDataDir());
        if (restored) {
            notes.add("已从基线快照 %s 回灌 %s".formatted(baselineCaseId, spec.writableDataDir()));
        } else {
            notes.add("未找到基线快照 %s，世界目录保持现状（可能由镜像首次生成）".formatted(baselineCaseId));
        }

        Path worldDir = spec.writableDataDir().resolve(LEVEL_NAME);
        Path datapacksDir = worldDir.resolve(DATAPACKS_DIR);
        try {
            Files.createDirectories(datapacksDir);
        } catch (IOException ex) {
            throw new UncheckedIOException("创建数据包目录失败: " + datapacksDir, ex);
        }
        String installed = install(packSource, datapacksDir);
        notes.add("数据包已放入 " + datapacksDir.resolve(installed));

        SandboxSpec wired = withLevel(spec);
        return new Prepared(wired, worldDir, datapacksDir, LEVEL_NAME,
                List.of(LEVEL_ARGUMENT, LEVEL_NAME), notes);
    }

    /** itzg 镜像按 LEVEL 选世界目录；只改环境变量，不动其它配额。 */
    private static SandboxSpec withLevel(SandboxSpec spec) {
        Map<String, String> env = new LinkedHashMap<>(spec.extraEnv());
        env.put("LEVEL", LEVEL_NAME);
        return new SandboxSpec(spec.caseId(), spec.image(), spec.serverType(), spec.serverVersion(),
                spec.memoryMb(), spec.cpus(), spec.jvmHeapMb(), spec.readOnlyMounts(),
                spec.writableDataDir(), spec.networkPolicy(), spec.rconPort(), spec.worldSnapshotEnabled(),
                spec.oomRestartLimit(), env, spec.labels());
    }

    private static String install(Path packSource, Path datapacksDir) {
        Objects.requireNonNull(packSource, "packSource");
        if (!Files.exists(packSource)) {
            throw new IllegalArgumentException("数据包路径不存在: " + packSource);
        }
        String name = packName(packSource);
        Path target = datapacksDir.resolve(name);
        if (Files.isDirectory(packSource)) {
            copyTree(packSource, target);
        } else if (isArchive(packSource)) {
            extractZip(packSource, target);
        } else {
            throw new IllegalArgumentException("数据包必须是目录或 zip: " + packSource);
        }
        return name;
    }

    private static String packName(Path packSource) {
        String fileName = packSource.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String safe = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "-");
        return safe.isBlank() ? "pack" : safe;
    }

    private static boolean isArchive(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".zip") || name.endsWith(".jar");
    }

    private static void copyTree(Path source, Path target) {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path entry : paths.toList()) {
                Path destination = target.resolve(source.relativize(entry).toString());
                if (Files.isDirectory(entry)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(entry, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("复制数据包目录失败: " + source, ex);
        }
    }

    private static void extractZip(Path archive, Path target) {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                // 解压前归一化并校验落点，挡住 ../ 形式的路径穿越
                Path destination = target.resolve(entry.getName()).normalize();
                if (!destination.startsWith(target)) {
                    throw new IllegalArgumentException("数据包压缩包含越界条目: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    copyEntry(zip, destination);
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("解压数据包失败: " + archive, ex);
        }
    }

    private static void copyEntry(InputStream in, Path destination) throws IOException {
        Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
    }
}
