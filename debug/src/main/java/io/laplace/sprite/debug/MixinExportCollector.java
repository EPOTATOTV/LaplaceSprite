package io.laplace.sprite.debug;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Mixin 织入快照收集器（15.1）：扫 {@code .mixin.out} 统计织入类与反编译源码，并可打包成 zip
 * 交给 EvidenceVault 归档。
 */
public final class MixinExportCollector {

    private static final String CLASS_SUFFIX = ".class";

    private static final String SOURCE_SUFFIX = ".java";

    private final Path exportDir;

    public MixinExportCollector(Path instanceRoot, MixinExportOptions options) {
        this.exportDir = options.exportPath(instanceRoot);
    }

    public Path exportDir() {
        return exportDir;
    }

    /** 目录不存在时返回空快照，不抛异常：没导出和导出为空都得能照常出报告。 */
    public MixinExportSnapshot snapshot(String instanceId) {
        if (!Files.isDirectory(exportDir)) {
            return new MixinExportSnapshot(instanceId, exportDir, 0, 0, List.of());
        }
        List<String> targets = new ArrayList<>();
        int classes = 0;
        int sources = 0;
        try (Stream<Path> paths = Files.walk(exportDir)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String name = path.getFileName().toString();
                String relative = exportDir.relativize(path).toString().replace('\\', '/');
                if (name.endsWith(CLASS_SUFFIX)) {
                    classes++;
                    targets.add(relative.substring(0, relative.length() - CLASS_SUFFIX.length()).replace('/', '.'));
                } else if (name.endsWith(SOURCE_SUFFIX)) {
                    sources++;
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("扫描 Mixin 导出目录失败: " + exportDir, ex);
        }
        targets.sort(Comparator.naturalOrder());
        return new MixinExportSnapshot(instanceId, exportDir, classes, sources, targets);
    }

    /** 打包导出目录；目录不存在时产出只含空结构的 zip，报告侧据此显示「无导出」。 */
    public Path bundleZip(String instanceId, Path zipTarget) {
        try {
            if (zipTarget.getParent() != null) {
                Files.createDirectories(zipTarget.getParent());
            }
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipTarget))) {
                zip.setComment("mixin export of " + instanceId);
                if (Files.isDirectory(exportDir)) {
                    try (Stream<Path> paths = Files.walk(exportDir)) {
                        for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                            zip.putNextEntry(new ZipEntry(
                                    exportDir.relativize(path).toString().replace('\\', '/')));
                            Files.copy(path, zip);
                            zip.closeEntry();
                        }
                    }
                }
            }
            return zipTarget;
        } catch (IOException ex) {
            throw new UncheckedIOException("打包 Mixin 导出目录失败: " + zipTarget, ex);
        }
    }
}
