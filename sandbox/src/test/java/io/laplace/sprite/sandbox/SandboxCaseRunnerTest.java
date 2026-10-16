package io.laplace.sprite.sandbox;

import io.laplace.sprite.core.model.RunStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 沙箱执行器（15.4）：OOM 判定、退避重启、审计拒启动、快照回灌、docker 缺席降级。 */
class SandboxCaseRunnerTest {

    private final SandboxAuditor auditor = new SandboxAuditor();

    @Test
    void oomKilledIsReportedAsFailureEvenWhenRetrySucceeds(@TempDir Path tempDir) {
        SandboxSpec spec = spec(tempDir);
        AtomicInteger attempts = new AtomicInteger();
        List<Long> sleeps = new ArrayList<>();
        SandboxCaseRunner.ContainerExecutor executor = (sandbox, attempt) -> {
            attempts.incrementAndGet();
            return attempt == 0
                    ? SandboxCaseRunner.ContainerOutcome.completed(SandboxLauncher.OOM_EXIT_CODE, true,
                            List.of("container killed"))
                    : SandboxCaseRunner.ContainerOutcome.completed(0, false, List.of("server ready"));
        };
        SandboxCaseRunner runner = new SandboxCaseRunner(auditor, new WorldSnapshotStore(tempDir.resolve("snap")),
                OomBackoffPolicy.defaults(), executor, sleeps::add);

        SandboxRunResult result = runner.run(spec);

        assertEquals(2, attempts.get(), "OOM 后应退避重启一次");
        assertEquals(RunStatus.FAILED, result.status(), "重试跑通也不能改判通过");
        assertFalse(result.passed());
        assertTrue(result.oomKilled());
        assertEquals(1, result.restarts());
        assertEquals(List.of(1000L), sleeps, "首次退避等待 1s");
        assertTrue(result.logs().stream().anyMatch(line -> line.contains("内存不足")), "结论要写成内存不足一类");
        assertTrue(result.worldRestored(), "用例结束后回灌基线快照");
    }

    @Test
    void oomBackoffIsExponentialAndBudgetIsFinite(@TempDir Path tempDir) {
        SandboxSpec spec = spec(tempDir);
        AtomicInteger attempts = new AtomicInteger();
        List<Long> sleeps = new ArrayList<>();
        SandboxCaseRunner.ContainerExecutor alwaysOom = (sandbox, attempt) -> {
            attempts.incrementAndGet();
            return SandboxCaseRunner.ContainerOutcome.completed(137, true, List.of("OOMKilled"));
        };
        SandboxCaseRunner runner = new SandboxCaseRunner(auditor, new WorldSnapshotStore(tempDir.resolve("snap")),
                OomBackoffPolicy.defaults(), alwaysOom, sleeps::add);

        SandboxRunResult result = runner.run(spec);

        assertEquals(4, attempts.get(), "默认名额 3 次，加上首跑共 4 次");
        assertEquals(3, result.restarts());
        assertEquals(List.of(1000L, 2000L, 4000L), sleeps, "退避按指数增长");
        assertEquals(RunStatus.FAILED, result.status());
        assertTrue(result.oomKilled());
    }

    @Test
    void backoffPolicyCapsAndValidates() {
        OomBackoffPolicy policy = OomBackoffPolicy.defaults();
        assertEquals(1000L, policy.delayFor(1).toMillis());
        assertEquals(2000L, policy.delayFor(2).toMillis());
        assertEquals(4000L, policy.delayFor(3).toMillis());
        assertEquals(8000L, policy.delayFor(4).toMillis());
        assertEquals(16000L, policy.delayFor(5).toMillis());
        assertEquals(30000L, policy.delayFor(6).toMillis(), "等待上限封顶");
        assertTrue(policy.hasBudget(2));
        assertFalse(policy.hasBudget(3));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> policy.delayFor(0));
    }

    @Test
    void sensitiveSpecIsRejectedBeforeLaunch(@TempDir Path tempDir) {
        SandboxSpec spec = new SandboxSpec("case-audit", "img", "PAPER", "1.21.11", 1024, 1.0, 717,
                List.of(SandboxMount.readOnly(System.getProperty("user.home"), "/data/mods")),
                tempDir.resolve("data"), SandboxNetworkPolicy.isolate("case-audit"), 25575, true, 1,
                Map.of(), Map.of());
        AtomicInteger launches = new AtomicInteger();
        SandboxCaseRunner runner = new SandboxCaseRunner(auditor, new WorldSnapshotStore(tempDir.resolve("snap")),
                OomBackoffPolicy.defaults(), (sandbox, attempt) -> {
                    launches.incrementAndGet();
                    return SandboxCaseRunner.ContainerOutcome.completed(0, false, List.of());
                }, millis -> {
                });

        SandboxRunResult result = runner.run(spec);

        assertEquals(0, launches.get(), "审计命中就不得拉起容器");
        assertEquals(RunStatus.FAILED, result.status());
        assertEquals(1, result.sensitiveFindings().size());
        assertTrue(result.logs().stream().anyMatch(line -> line.contains("敏感路径审计拒绝启动")));
    }

    @Test
    void missingDockerYieldsSkippedNotFailure(@TempDir Path tempDir) {
        SandboxLauncher launcher = new SandboxLauncher("definitely-not-a-docker-binary-xyz");
        SandboxCaseRunner runner = new SandboxCaseRunner(auditor, new WorldSnapshotStore(tempDir.resolve("snap")),
                OomBackoffPolicy.defaults(), launcher::runOnce, millis -> {
                });

        SandboxRunResult result = runner.run(spec(tempDir));

        assertEquals(RunStatus.SKIPPED, result.status(), "环境不具备是非致命，不判失败");
        assertFalse(result.oomKilled());
        assertEquals(0, result.restarts());
        assertTrue(result.logs().stream().anyMatch(line -> line.contains("unavailable")));
    }

    @Test
    void nonZeroExitWithoutOomIsPlainFailure(@TempDir Path tempDir) {
        SandboxCaseRunner runner = new SandboxCaseRunner(auditor, new WorldSnapshotStore(tempDir.resolve("snap")),
                OomBackoffPolicy.defaults(),
                (sandbox, attempt) -> SandboxCaseRunner.ContainerOutcome.completed(1, false, List.of("crash")),
                millis -> {
                });

        SandboxRunResult result = runner.run(spec(tempDir));

        assertEquals(RunStatus.FAILED, result.status());
        assertFalse(result.oomKilled());
        assertEquals(0, result.restarts());
    }

    private static SandboxSpec spec(Path tempDir) {
        return SandboxSpec.forTier("case-run-1", SandboxTier.STANDARD, "itzg/minecraft-server:java21", "PAPER",
                "1.21.11", List.of(SandboxMount.readOnly(tempDir.resolve("mods").toString(), "/data/mods")),
                tempDir.resolve("data"), 25575);
    }
}
