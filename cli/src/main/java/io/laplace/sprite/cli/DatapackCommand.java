package io.laplace.sprite.cli;

import io.laplace.sprite.datapack.PackFormatTable;
import io.laplace.sprite.datapack.PackMeta;
import io.laplace.sprite.datapack.PackMetaError;
import io.laplace.sprite.datapack.PackMetaParser;
import io.laplace.sprite.datapack.PackMetaResult;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.Callable;

/**
 * {@code laplace datapack}：15.8 数据包 / 资源包测试轨的装配层。
 *
 * <p>解析 pack.mcmeta 并对一组服务端版本判定 pack_format 兼容性；不兼容的版本列按文档口径
 * 记为「加载阶段失败」，不进入冒烟执行。</p>
 */
@Command(name = "datapack",
        description = "数据包冒烟轨：pack.mcmeta 解析、pack_format 版本兼容矩阵、加载失败特征",
        mixinStandardHelpOptions = true)
final class DatapackCommand implements Callable<Integer> {

    @Option(names = "--pack", required = true, description = "数据包根目录（含 pack.mcmeta）")
    Path pack;

    @Option(names = "--pack-meta", description = "直接指定 pack.mcmeta 文件，覆盖 --pack")
    Path packMeta;

    @Option(names = "--versions", defaultValue = "1.20.6,1.21,1.21.4,1.21.5,1.21.7",
            description = "待判定兼容性的服务端版本，逗号分隔")
    String versions;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        var writer = spec.commandLine().getOut();
        PackMetaParser parser = new PackMetaParser();
        PackMetaResult result = packMeta != null ? parser.parseFile(packMeta) : parser.parsePack(pack);

        if (!result.ok()) {
            PackMetaError error = result.error();
            writer.println("数据包解析失败（15.8 加载错误特征路由）");
            writer.println("  文件 " + error.file() + " · JSON 路径 " + error.jsonPath()
                    + " · 类别 " + error.kind());
            writer.println("  说明 " + error.describe());
            return 1;
        }

        PackMeta meta = result.meta();
        writer.println("数据包 " + pack.toAbsolutePath());
        writer.printf("  pack_format %d · 描述 %s%n", meta.packFormat(), meta.description());

        PackFormatTable formats = new PackFormatTable();
        List<String> targets = Arrays.stream(versions.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        writer.println("  版本兼容矩阵（不兼容即加载阶段失败）");
        int failed = 0;
        for (String version : targets) {
            boolean compatible = formats.compatible(version, meta);
            OptionalInt expected = formats.formatFor(version);
            writer.printf("    %-10s 通过=%-5s 期望 pack_format=%s%n", version, compatible,
                    expected.isPresent() ? expected.getAsInt() : "未知");
            if (!compatible) {
                failed++;
            }
        }
        writer.printf("  版本列 %d 个，不兼容 %d 个%n", targets.size(), failed);
        if (!Files.isDirectory(pack) && packMeta == null) {
            writer.println("  提示：--pack 指向的目录不存在");
        }
        return failed == 0 ? 0 : 1;
    }
}
