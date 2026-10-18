package io.laplace.sprite.adapters;

import io.laplace.sprite.adapters.gametest.GameTestLoader;
import io.laplace.sprite.adapters.gametest.GameTestRunner;
import io.laplace.sprite.adapters.gametest.GameTestSuites;
import io.laplace.sprite.adapters.gametest.NativeGameTestResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GameTest 执行器（L-02）：Gradle 任务调用、日志解析与失败归因。 */
class GameTestRunnerTest {

    private static final List<String> PASSING = List.of(
            "[12:00:00] [Server thread/INFO]: test.laplace.legit.WalkTest passed!",
            "[12:00:00] [Server thread/INFO]: 1 tests passed, 0 tests failed.");

    private static final List<String> FAILING = List.of(
            "[12:00:00] [Server thread/INFO]: test.laplace.legit.WalkTest passed!",
            "[12:00:01] [Server thread/INFO]: test.laplace.cheat.ReachTest failed! reach 4.7 exceeded 3.0",
            "[12:00:01] [Server thread/INFO]: 1 tests passed, 1 tests failed.");

    @TempDir
    Path tempDir;

    @Test
    void passingRunUsesTheLoaderGradleTask() {
        GameTestRunner runner = new GameTestRunner((command, workDir, timeout) ->
                new GameTestRunner.Execution(0, PASSING, 1200, false));

        GameTestRunner.Report report = runner.run(GameTestLoader.FORGE, tempDir);

        assertTrue(report.passed(), report.error());
        assertEquals(GameTestLoader.FORGE, report.loader());
        assertEquals("runGameTestServer", report.command().get(report.command().size() - 1));
        assertEquals(1, report.total());
        assertEquals(0, report.failed());
    }

    @Test
    void failedCasesAreReportedEvenWhenGradleExitsWithOne() {
        GameTestRunner runner = new GameTestRunner((command, workDir, timeout) ->
                new GameTestRunner.Execution(1, FAILING, 900, false));

        GameTestRunner.Report report = runner.run(GameTestLoader.NEOFORGE, tempDir);

        assertFalse(report.passed());
        assertEquals(1, report.failed());
        assertEquals(1, report.exitCode());
        assertTrue(report.error() == null, "日志解析成功时不应记解析错误");
    }

    @Test
    void timeoutAndMissingCasesAreReportedInsteadOfPassing() {
        GameTestRunner timeoutRunner = new GameTestRunner((command, workDir, timeout) ->
                new GameTestRunner.Execution(-1, PASSING, 5000, true));
        GameTestRunner.Report timedOut = timeoutRunner.run(GameTestLoader.QUILT, tempDir);
        assertFalse(timedOut.passed());
        assertTrue(timedOut.timedOut());

        GameTestRunner noCases = new GameTestRunner((command, workDir, timeout) ->
                new GameTestRunner.Execution(0, List.of("BUILD SUCCESSFUL"), 100, false));
        GameTestRunner.Report empty = noCases.run(GameTestLoader.FABRIC, tempDir);
        assertFalse(empty.passed());
        assertTrue(empty.error().contains("没有可识别"));
    }

    @Test
    void missingProjectDirectoryDoesNotInvokeTheExecutor() {
        boolean[] invoked = {false};
        GameTestRunner runner = new GameTestRunner((command, workDir, timeout) -> {
            invoked[0] = true;
            return new GameTestRunner.Execution(0, PASSING, 1, false);
        });

        GameTestRunner.Report report = runner.run(GameTestLoader.FABRIC,
                tempDir.resolve("missing"), Duration.ofSeconds(1));

        assertFalse(report.passed());
        assertFalse(invoked[0]);
        assertTrue(report.error().contains("项目目录不存在"));
    }

    @Test
    void fromLogToleratesGradleExitCodeButRejectsEmptyLogs() {
        NativeGameTestResult failed = GameTestSuites.fromLog(FAILING);

        assertEquals(2, failed.total());
        assertEquals(1, failed.failed());
        assertEquals(1, failed.exitCode(), "日志路径把退出码归一为失败用例数");

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> GameTestSuites.fromLog(List.of("BUILD FAILED")));
    }
}
