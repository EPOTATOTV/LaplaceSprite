package io.laplace.sprite.cli;

import io.laplace.sprite.render.PixelDiff;
import io.laplace.sprite.render.PixelDiffOptions;
import io.laplace.sprite.render.PixelDiffResult;
import io.laplace.sprite.render.RenderSuiteRunner;
import io.laplace.sprite.render.Screenshot;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
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

    @Option(names = "--golden", description = "金标准截图 PNG（单图比对）")
    Path golden;

    @Option(names = "--actual", description = "实拍截图 PNG（单图比对）")
    Path actual;

    @Option(names = "--tolerance", defaultValue = "0.5", description = "允许的差异像素百分比")
    double tolerance;

    @Option(names = "--suite", description = "按 golden / actual 目录跑完整渲染套件")
    boolean suite;

    @Option(names = "--golden-root", description = "套件模式：golden 根目录")
    Path goldenRoot;

    @Option(names = "--actual-root", description = "套件模式：实拍截图根目录")
    Path actualRoot;

    @Option(names = "--report", description = "套件模式：可选 Markdown 报告路径")
    Path report;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        if (suite) {
            return runSuite();
        }
        if (golden == null || actual == null) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "单图比对需要 --golden 与 --actual；批量比对用 --suite --golden-root --actual-root");
        }
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

    private int runSuite() {
        if (goldenRoot == null || actualRoot == null) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    "--suite 需要 --golden-root 与 --actual-root");
        }
        var writer = spec.commandLine().getOut();
        RenderSuiteRunner.Report result = new RenderSuiteRunner().run(goldenRoot, actualRoot);
        writer.println("渲染套件（L-06）");
        writer.printf("  golden %s%n  actual %s%n", goldenRoot.toAbsolutePath(), actualRoot.toAbsolutePath());
        writer.printf("  通过 %d · 失败 %d · 缺失 %d%n",
                result.count(RenderSuiteRunner.Status.PASSED),
                result.count(RenderSuiteRunner.Status.FAILED),
                result.count(RenderSuiteRunner.Status.MISSING));
        result.cases().forEach(testCase -> writer.printf("  [%s] %s/%s/%s — %s%n",
                testCase.status(), testCase.mcVersion(), testCase.backend().id(), testCase.caseId(),
                testCase.reason()));
        if (report != null) {
            writeReport(result, report);
            writer.printf("  报告 %s%n", report.toAbsolutePath());
        }
        writer.println("  结果：" + (result.passed() ? "通过" : "未通过"));
        return result.passed() ? 0 : 1;
    }

    private static void writeReport(RenderSuiteRunner.Report result, Path target) {
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.writeString(target, result.toMarkdown(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("写入渲染报告失败: " + target, ex);
        }
    }
}
