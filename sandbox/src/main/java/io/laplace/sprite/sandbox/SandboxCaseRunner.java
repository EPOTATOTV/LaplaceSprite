package io.laplace.sprite.sandbox;

import io.laplace.sprite.core.model.RunStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 沙箱用例执行器（15.4）：审计 → 基线快照 → 跑容器（OOM 退避重启）→ 恢复世界 → 出结论。
 *
 * <p>容器执行被抽成 {@link ContainerExecutor}：真实实现是 {@link SandboxLauncher}，测试用假实现，
 * 因此 OOM 判定与快照恢复都能离线验证。</p>
 */
public final class SandboxCaseRunner {

    /** OOM 被杀的结果分类，独立成句，报告里一眼能捞出来。 */
    public static final String OOM_FAILURE = "内存不足（容器被 OOM killer 终止）";

    /** 一次容器运行的结果。 */
    public record ContainerOutcome(int exitCode, boolean oomKilled, boolean unavailable, List<String> logs) {

        public ContainerOutcome {
            logs = logs == null ? List.of() : List.copyOf(logs);
        }

        public static ContainerOutcome completed(int exitCode, boolean oomKilled, List<String> logs) {
            return new ContainerOutcome(exitCode, oomKilled, false, logs);
        }

        /** 环境不具备（docker 不在等）：非致命，用例判 SKIPPED 而不是失败。 */
        public static ContainerOutcome unavailable(String reason) {
            return new ContainerOutcome(-1, false, true, List.of(reason));
        }

        public boolean succeeded() {
            return !unavailable && exitCode == 0;
        }
    }

    @FunctionalInterface
    public interface ContainerExecutor {
        ContainerOutcome run(SandboxSpec spec, int attempt);
    }

    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis);
    }

    private final SandboxAuditor auditor;
    private final WorldSnapshotStore snapshots;
    private final OomBackoffPolicy backoff;
    private final ContainerExecutor executor;
    private final Sleeper sleeper;

    public SandboxCaseRunner(SandboxAuditor auditor, WorldSnapshotStore snapshots, OomBackoffPolicy backoff,
                             ContainerExecutor executor, Sleeper sleeper) {
        this.auditor = Objects.requireNonNull(auditor, "auditor");
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.backoff = Objects.requireNonNull(backoff, "backoff");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    /** 真实退避用 Thread.sleep；测试注入记录型 sleeper，不真等。 */
    public static Sleeper realSleeper() {
        return millis -> {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        };
    }

    public SandboxRunResult run(SandboxSpec spec) {
        List<String> logs = new ArrayList<>();
        List<SandboxAudit> findings = auditor.audit(spec);
        List<SandboxAudit> sensitive = findings.stream().filter(SandboxAudit::sensitive).toList();
        if (!sensitive.isEmpty()) {
            sensitive.forEach(finding -> logs.add("敏感路径审计拒绝启动: %s（%s）"
                    .formatted(finding.path(), finding.reason())));
            return new SandboxRunResult(spec.caseId(), RunStatus.FAILED, false, 0, false, findings, logs);
        }

        boolean snapshotTaken = false;
        if (spec.worldSnapshotEnabled()) {
            snapshots.snapshotBaseline(spec.caseId(), spec.writableDataDir());
            snapshotTaken = true;
            logs.add("已建立 /data 基线快照: " + snapshots.baselineDirectory(spec.caseId()));
        }

        int attempt = 0;
        int restarts = 0;
        boolean oomKilled = false;
        RunStatus status = RunStatus.FAILED;
        while (true) {
            ContainerOutcome outcome = executor.run(spec, attempt);
            logs.addAll(outcome.logs());
            if (outcome.unavailable()) {
                status = RunStatus.SKIPPED;
                break;
            }
            if (outcome.oomKilled()) {
                oomKilled = true;
                if (backoff.hasBudget(restarts)) {
                    restarts++;
                    long delay = backoff.delayFor(restarts).toMillis();
                    logs.add("OOM 退避重启 %d/%d：%d 未达标，等待 %dms"
                            .formatted(restarts, backoff.maxRestarts(), attempt, delay));
                    sleeper.sleep(delay);
                    attempt++;
                    continue;
                }
                logs.add("OOM 重启名额用尽（%d 次）".formatted(restarts));
                status = RunStatus.FAILED;
                break;
            }
            status = outcome.succeeded() ? RunStatus.PASSED : RunStatus.FAILED;
            break;
        }

        if (oomKilled) {
            // 关键是这里：只要被 OOM 杀过就记成内存不足，重试跑通也不改判
            status = RunStatus.FAILED;
            logs.add(OOM_FAILURE);
        }

        boolean worldRestored = false;
        if (snapshotTaken) {
            worldRestored = snapshots.restoreBaseline(spec.caseId(), spec.writableDataDir());
            logs.add(worldRestored ? "已回灌 /data 基线快照，用例间零污染" : "基线快照缺失，/data 未恢复");
        }
        return new SandboxRunResult(spec.caseId(), status, oomKilled, restarts, worldRestored, findings,
                logs);
    }
}
