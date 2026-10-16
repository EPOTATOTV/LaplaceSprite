package io.laplace.sprite.debug;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 源码映射生成器（15.1）：调用工程里配置的 gradle 任务（默认 {@code genSources}）产出反编译 MC 源码。
 *
 * <p>两条降级路径都要说明白：非 Loom 工程直接跳过；gradle 不存在或任务失败时把原因原样写进
 * {@code notes}，不假装生成过。</p>
 */
public final class SourceMapGenerator {

    public static final String DEFAULT_TASK = "genSources";

    private static final String LOOM_MARKER = "fabric-loom";

    private static final String GENERATOR = "gradle genSources（Fabric Loom）";

    private final String gradleExecutable;

    public SourceMapGenerator() {
        this("gradle");
    }

    public SourceMapGenerator(String gradleExecutable) {
        if (gradleExecutable == null || gradleExecutable.isBlank()) {
            throw new IllegalArgumentException("gradle 可执行文件不能为空");
        }
        this.gradleExecutable = gradleExecutable;
    }

    public SourceMapReport generate(SourceMapRequest request) {
        String instanceId = request.instanceId();
        if (!isLoomProject(request.projectDir())) {
            return new SourceMapReport(instanceId, false, null, GENERATOR,
                    List.of("未在 %s 检测到 fabric-loom 插件，跳过源码映射；缺源码时断点打不进 MC 类"
                            .formatted(request.projectDir())));
        }
        List<String> command = List.of(gradleExecutable, request.gradleTask(), "-p",
                request.projectDir().toString());
        try {
            Process process = new ProcessBuilder(command)
                    .directory(request.projectDir().toFile())
                    .redirectErrorStream(true)
                    .start();
            // 先读干净输出再等退出：genSources 输出量可能超过管道缓冲，反过来等会死锁
            List<String> output = process.inputReader().lines().toList();
            process.waitFor();
            if (process.exitValue() != 0) {
                return new SourceMapReport(instanceId, false, null, GENERATOR,
                        List.of("gradle %s 退出码 %d，源码映射未生成；末行输出：%s"
                                .formatted(request.gradleTask(), process.exitValue(), lastLine(output))));
            }
            return new SourceMapReport(instanceId, true, request.sourceDir(), GENERATOR,
                    List.of("已执行 gradle %s；源码预期落在 %s（Loom 版本不同也可能落到 gradle 缓存）"
                            .formatted(request.gradleTask(), request.sourceDir())));
        } catch (IOException ex) {
            return new SourceMapReport(instanceId, false, null, GENERATOR,
                    List.of("未找到 gradle 可执行文件（%s），源码映射未生成".formatted(gradleExecutable)));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new SourceMapReport(instanceId, false, null, GENERATOR, List.of("生成源码被中断"));
        }
    }

    /** Loom 工程判定：构建脚本里出现 fabric-loom 插件。 */
    public boolean isLoomProject(Path projectDir) {
        for (String script : List.of("build.gradle", "build.gradle.kts")) {
            Path path = projectDir.resolve(script);
            if (Files.isRegularFile(path)) {
                try {
                    if (Files.readString(path, StandardCharsets.UTF_8).contains(LOOM_MARKER)) {
                        return true;
                    }
                } catch (IOException ex) {
                    return false;
                }
            }
        }
        return false;
    }

    private static String lastLine(List<String> output) {
        return output.isEmpty() ? "（无输出）" : output.get(output.size() - 1);
    }
}
