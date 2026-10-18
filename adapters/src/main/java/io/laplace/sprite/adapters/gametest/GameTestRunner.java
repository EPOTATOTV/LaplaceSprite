package io.laplace.sprite.adapters.gametest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * GameTest 执行器（升级实施文档 L-02）：把加载器入口落到真实 Gradle 任务，解析日志并给出结论。
 *
 * <p>四个加载器的任务名来自 {@link GameTestLoader}；执行结果不依赖 Gradle 退出码与失败用例数相等，
 * 而是先按日志解析，再把进程退出码作为附加判据。超时、脚本缺失、日志无用例都会如实记进报告。</p>
 */
public final class GameTestRunner {

    /** 默认单次任务超时。 */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(20);

    /** 外部进程执行接缝，测试用它替换真实 Gradle。 */
    @FunctionalInterface
    public interface Executor {
        Execution execute(List<String> command, Path workDir, Duration timeout) throws IOException, InterruptedException;
    }

    /**
     * 一次进程执行结果。
     *
     * @param exitCode   退出码；超时为 -1
     * @param lines      合并后的 stdout / stderr
     * @param durationMs 实际耗时
     * @param timedOut   是否超时
     */
    public record Execution(int exitCode, List<String> lines, long durationMs, boolean timedOut) {

        public Execution {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    /**
     * 一次 GameTest 运行报告。
     *
     * @param loader    加载器
     * @param command   实际执行命令
     * @param exitCode  进程退出码
     * @param total     用例总数
     * @param failed    失败用例数
     * @param durationMs 耗时
     * @param timedOut  是否超时
     * @param error     执行 / 解析错误；成功时为空
     */
    public record Report(GameTestLoader loader, List<String> command, int exitCode, int total, int failed,
                         long durationMs, boolean timedOut, String error) {

        public Report {
            command = command == null ? List.of() : List.copyOf(command);
        }

        public boolean passed() {
            return error == null && !timedOut && exitCode == 0 && failed == 0 && total > 0;
        }
    }

    private final Executor executor;

    public GameTestRunner() {
        this(GameTestRunner::runProcess);
    }

    public GameTestRunner(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public Report run(GameTestLoader loader, Path projectDir) {
        return run(loader, projectDir, DEFAULT_TIMEOUT);
    }

    public Report run(GameTestLoader loader, Path projectDir, Duration timeout) {
        Objects.requireNonNull(loader, "loader");
        Objects.requireNonNull(projectDir, "projectDir");
        List<String> command = command(loader, projectDir);
        if (!Files.isDirectory(projectDir)) {
            return new Report(loader, command, -1, 0, 0, 0, false,
                    "项目目录不存在: " + projectDir.toAbsolutePath());
        }
        try {
            Execution execution = executor.execute(command, projectDir, timeout);
            NativeGameTestResult result = GameTestSuites.fromLog(execution.lines());
            return new Report(loader, command, execution.exitCode(), result.total(), result.failed(),
                    execution.durationMs(), execution.timedOut(), null);
        } catch (IllegalArgumentException ex) {
            return new Report(loader, command, -1, 0, 0, 0, false, ex.getMessage());
        } catch (IOException ex) {
            return new Report(loader, command, -1, 0, 0, 0, false, "启动 GameTest 进程失败: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Report(loader, command, -1, 0, 0, 0, false, "GameTest 执行被中断");
        }
    }

    /** 四个加载器共用同一个 Gradle 包装脚本，只有任务名不同。 */
    public static List<String> command(GameTestLoader loader, Path projectDir) {
        Objects.requireNonNull(loader, "loader");
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (windows) {
            return List.of("cmd.exe", "/c", projectDir.resolve("gradlew.bat").toString(),
                    "--build-cache", loader.gradleTask());
        }
        return List.of(projectDir.resolve("gradlew").toString(), "--build-cache", loader.gradleTask());
    }

    /** 真实进程执行：输出落临时文件，避免管道写满阻塞，同时支持超时强制终止。 */
    static Execution runProcess(List<String> command, Path workDir, Duration timeout)
            throws IOException, InterruptedException {
        Path output = Files.createTempFile("laplace-gametest-", ".log");
        long started = System.nanoTime();
        Process process = new ProcessBuilder(command)
                .directory(workDir.toFile())
                .redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start();
        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
        long durationMs = (System.nanoTime() - started) / 1_000_000;
        List<String> lines = new ArrayList<>();
        try {
            // 控制台编码随平台变化；按字节解码并替换非法序列，避免日志里混入非 UTF-8 字符时直接失败。
            String text = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
            text.lines().forEach(lines::add);
        } finally {
            Files.deleteIfExists(output);
        }
        return new Execution(finished ? process.exitValue() : -1, lines, durationMs, !finished);
    }
}
