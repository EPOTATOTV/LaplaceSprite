package io.laplace.sprite.core.engine;

import io.laplace.sprite.core.model.LogEntry;
import io.laplace.sprite.core.model.OrchestratorCommand;
import io.laplace.sprite.core.model.Scenario;
import io.laplace.sprite.core.model.SimPlayerRegistry;
import io.laplace.sprite.core.model.TestRun;
import io.laplace.sprite.core.spi.AdapterRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 调度器（4.4 执行层 / 11.3 TestServer 编排控制台）：编排场景剧本文库、检测目标与运行器。
 */
public final class Orchestrator {

    private final Runner runner;
    private final AdapterRegistry adapters;
    private final List<LogEntry> logs = new CopyOnWriteArrayList<>();
    private final SimPlayerRegistry players = new SimPlayerRegistry();

    public Orchestrator(Runner runner, AdapterRegistry adapters) {
        this.runner = runner;
        this.adapters = adapters;
    }

    public TestRun runSuite(RunRequest request, Scenario scenario, List<SimulatedSession> sessions,
                            DetectionTarget target) {
        logs.add(LogEntry.info("orchestrator", "开始运行 %s / 场景 %s / 会话 %d 条"
                .formatted(request.runId(), scenario.id(), sessions.size())));
        TestRun run = runner.execute(request, scenario, sessions, target);
        logs.add(run.status() == io.laplace.sprite.core.model.RunStatus.PASSED
                ? LogEntry.info("orchestrator", "运行 %s 通过".formatted(run.id()))
                : LogEntry.flag("orchestrator", "运行 %s 未通过（误报 %d / 检出 %d）"
                        .formatted(run.id(), run.metrics().falsePositives(), run.metrics().detectedSamples())));
        return run;
    }

    /** 矩阵运行：多平台 / 多版本并行（14.7 组合爆炸控制的执行侧）。 */
    public List<TestRun> runMatrix(List<MatrixJob> jobs, int parallelism) {
        if (jobs.isEmpty()) {
            return List.of();
        }
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, parallelism));
        try {
            List<Future<TestRun>> futures = new ArrayList<>();
            for (MatrixJob job : jobs) {
                futures.add(pool.submit(() -> runSuite(job.request(), job.scenario(), job.sessions(),
                        job.target())));
            }
            List<TestRun> runs = new ArrayList<>();
            for (Future<TestRun> future : futures) {
                runs.add(future.get());
            }
            return runs;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("矩阵运行被中断", ex);
        } catch (java.util.concurrent.ExecutionException ex) {
            throw new IllegalStateException("矩阵运行失败", ex.getCause());
        } finally {
            pool.shutdown();
            try {
                pool.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** /ls 命令面板（11.4）：inspect / teleport / scenario / result。 */
    public OrchestratorCommand handle(OrchestratorCommand command) {
        String receipt = switch (command.name()) {
            case "inspect" -> "已打开查端会话: " + String.join(" ", command.args());
            case "teleport" -> "已传送管理员视角: " + String.join(" ", command.args());
            case "scenario" -> "场景剧本指令已下发: " + String.join(" ", command.args());
            case "result" -> "已导出最近运行结果: " + String.join(" ", command.args());
            default -> "未知子命令: " + command.name();
        };
        logs.add(LogEntry.info("command", "/ls " + command.name() + " → " + receipt));
        return command.withReceipt(receipt);
    }

    public List<LogEntry> logs() {
        return List.copyOf(logs);
    }

    public SimPlayerRegistry players() {
        return players;
    }

    public AdapterRegistry adapters() {
        return adapters;
    }

    /**
     * 矩阵作业（一次「平台 × 版本 × 场景」单元）。
     */
    public record MatrixJob(RunRequest request, Scenario scenario, List<SimulatedSession> sessions,
                            DetectionTarget target) {
    }
}
