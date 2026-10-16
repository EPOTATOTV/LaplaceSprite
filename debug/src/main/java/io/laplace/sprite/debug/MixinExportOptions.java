package io.laplace.sprite.debug;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Mixin 织入导出选项（15.1）：调试实例默认追加 {@code -Dmixin.debug.export=true}，
 * 结束后把 {@code .mixin.out/}（织入后字节码 + Fernflower 反编译源码）作为证据收进报告。
 *
 * @param exportEnabled    是否导出织入后的类
 * @param exportDir        导出目录，Mixin 默认 .mixin.out
 * @param decompileEnabled 是否同时反编译导出的类
 */
public record MixinExportOptions(boolean exportEnabled, String exportDir, boolean decompileEnabled) {

    public static final String DEFAULT_EXPORT_DIR = ".mixin.out";

    private static final String EXPORT_PROPERTY = "-Dmixin.debug.export=true";

    private static final String EXPORT_DIR_PROPERTY = "-Dmixin.debug.export.dir=";

    private static final String DECOMPILE_PROPERTY = "-Dmixin.debug.export.decompile=true";

    public MixinExportOptions {
        if (exportDir == null || exportDir.isBlank()) {
            throw new IllegalArgumentException("导出目录不能为空");
        }
    }

    /** 调试实例默认档：导出 + 反编译。 */
    public static MixinExportOptions devDefault() {
        return new MixinExportOptions(true, DEFAULT_EXPORT_DIR, true);
    }

    public static MixinExportOptions disabled() {
        return new MixinExportOptions(false, DEFAULT_EXPORT_DIR, false);
    }

    public Path exportPath(Path instanceRoot) {
        return instanceRoot.resolve(exportDir);
    }

    /** 追加到启动命令的 JVM 参数；没开导出就是空列表。 */
    public List<String> jvmArguments() {
        if (!exportEnabled) {
            return List.of();
        }
        List<String> args = new ArrayList<>();
        args.add(EXPORT_PROPERTY);
        args.add(EXPORT_DIR_PROPERTY + exportDir);
        if (decompileEnabled) {
            args.add(DECOMPILE_PROPERTY);
        }
        return List.copyOf(args);
    }
}
