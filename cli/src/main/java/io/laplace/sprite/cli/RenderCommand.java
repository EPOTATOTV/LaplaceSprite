package io.laplace.sprite.cli;

import io.laplace.sprite.render.PixelDiff;
import io.laplace.sprite.render.PixelDiffOptions;
import io.laplace.sprite.render.PixelDiffResult;
import io.laplace.sprite.render.Screenshot;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * {@code laplace render}：15.12 客户端渲染级测试轨的像素比对。
 *
 * <p>只做截图像素 diff（默认 0.5% 像素容差，与 Fabric 客户端 Gametest 的口径一致）；
 * 渲染后端矩阵与 shader 冒烟由矩阵层调用，不在命令行单次比对里展开。</p>
 */
@Command(name = "render",
        description = "渲染级测试轨：金标准截图与实拍截图的像素 diff（默认 0.5% 容差）",
        mixinStandardHelpOptions = true)
final class RenderCommand implements Callable<Integer> {

    @Option(names = "--golden", required = true, description = "金标准截图 PNG")
    Path golden;

    @Option(names = "--actual", required = true, description = "实拍截图 PNG")
    Path actual;

    @Option(names = "--tolerance", defaultValue = "0.5", description = "允许的差异像素百分比")
    double tolerance;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        if (!Files.isRegularFile(golden) || !Files.isRegularFile(actual)) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "截图不存在: " + (Files.isRegularFile(golden) ? actual : golden).toAbsolutePath());
        }
        var writer = spec.commandLine().getOut();
        Screenshot goldenShot = Screenshot.fromPath(golden);
        Screenshot actualShot = Screenshot.fromPath(actual);
        PixelDiffResult result = PixelDiff.compare(goldenShot, actualShot,
                new PixelDiffOptions(tolerance, false, 0));

        writer.println("渲染级测试轨（15.12） · 容差 " + tolerance + "%");
        writer.printf("  金标准 %dx%d · 实拍 %dx%d%n", goldenShot.width(), goldenShot.height(),
                actualShot.width(), actualShot.height());
        if (result.sizeMismatch()) {
            writer.println("  结果：尺寸不一致，直接判失败（不缩放对齐）");
            return 1;
        }
        writer.printf("  差异像素 %d / %d（%.4f%%）%n", result.differingPixels(), result.totalPixels(),
                result.diffPercent());
        writer.println("  结果：" + (result.passed() ? "通过" : "未通过"));
        writer.println("  说明：VulkanMod 格按文档只做「启动 + 渲染一帧」冒烟，不进入视觉 diff 门禁");
        return result.passed() ? 0 : 1;
    }
}
