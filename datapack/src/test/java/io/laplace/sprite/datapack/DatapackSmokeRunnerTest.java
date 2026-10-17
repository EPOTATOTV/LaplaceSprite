package io.laplace.sprite.datapack;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 数据包冒烟执行器（15.8）：DSL 顺序、断言后端与版本门控。 */
class DatapackSmokeRunnerTest {

    private static final String LIST_REPLY =
            "There are 2 data packs enabled: [vanilla (built-in)], [file/smoke (world)]";
    private static final String FUNCTION_OK = "smoke ok\nSteve has 7 [laplace_smoke]";

    @Test
    void runsDslInDocumentedOrderAndPasses() {
        RecordingConsole console = new RecordingConsole(Map.of(
                DatapackSmokeRunner.COMMAND_DATAPACK_LIST, LIST_REPLY,
                DatapackSmokeRunner.COMMAND_RELOAD, "Reloaded 2 data packs",
                "/function smoke:check", FUNCTION_OK));

        DatapackRunResult result = new DatapackSmokeRunner(console, "1.21.4").run(endsWithCase());

        assertTrue(result.passed(), () -> "应通过，附注: " + result.notes());
        assertTrue(result.enabled());
        assertNull(result.failedStep());
        assertEquals(List.of(DatapackSmokeRunner.COMMAND_DATAPACK_LIST, DatapackSmokeRunner.COMMAND_RELOAD,
                "/function smoke:check"), result.commands(), "文档顺序不可打乱");
        assertEquals(FUNCTION_OK, result.functionOutput());
        assertTrue(result.scoreboard().contains("Steve has 7 [laplace_smoke]"));
    }

    @Test
    void failsWhenFunctionOutputDoesNotEndWithExpectedSuffix() {
        RecordingConsole console = new RecordingConsole(Map.of(
                DatapackSmokeRunner.COMMAND_DATAPACK_LIST, LIST_REPLY,
                DatapackSmokeRunner.COMMAND_RELOAD, "Reloaded 2 data packs",
                "/function smoke:check", "smoke ok\nSteve has 3 [laplace_smoke]"));

        DatapackRunResult result = new DatapackSmokeRunner(console, "1.21.4").run(endsWithCase());

        assertFalse(result.passed());
        assertEquals("/function smoke:check", result.failedStep());
        assertTrue(result.notes().contains("未以"));
    }

    @Test
    void failsAtEnableStageWhenPackIsNotListed() {
        RecordingConsole console = new RecordingConsole(Map.of(
                DatapackSmokeRunner.COMMAND_DATAPACK_LIST, "There are 1 data packs enabled: [vanilla]"));

        DatapackRunResult result = new DatapackSmokeRunner(console, "1.21.4").run(endsWithCase());

        assertFalse(result.passed());
        assertFalse(result.enabled());
        assertEquals(DatapackSmokeRunner.COMMAND_DATAPACK_LIST, result.failedStep());
        assertEquals(List.of(DatapackSmokeRunner.COMMAND_DATAPACK_LIST), result.commands(),
                "包没启用就不该继续 reload / function");
    }

    @Test
    void fallsBackToEndsWithBefore1215() {
        RecordingConsole console = new RecordingConsole(Map.of(
                DatapackSmokeRunner.COMMAND_DATAPACK_LIST, LIST_REPLY,
                DatapackSmokeRunner.COMMAND_RELOAD, "Reloaded 2 data packs",
                "/function smoke:check", FUNCTION_OK));

        DatapackRunResult result = new DatapackSmokeRunner(console, "1.21.4").run(gameTestCase());

        assertTrue(result.passed());
        assertNotNull(result.notes());
        assertTrue(result.notes().contains("回退为 ENDS_WITH"), "回退要如实记录: " + result.notes());
        assertFalse(result.commands().contains(DatapackSmokeRunner.COMMAND_TEST_CREATE),
                "1.21.4 没有 /test，不应下发");
    }

    @Test
    void usesGameTestPathFrom1215() {
        RecordingConsole console = new RecordingConsole(Map.of(
                DatapackSmokeRunner.COMMAND_DATAPACK_LIST, LIST_REPLY,
                DatapackSmokeRunner.COMMAND_RELOAD, "Reloaded 2 data packs",
                DatapackSmokeRunner.COMMAND_TEST_CREATE, "Test structure created at 0 4 0",
                DatapackSmokeRunner.COMMAND_TEST_LOCATE, "Nearest test structure at 0 4 0",
                "/function smoke:check", "Test passed!",
                DatapackSmokeRunner.COMMAND_TEST_CLEARALL, "Cleared all test structures"));

        DatapackRunResult result = new DatapackSmokeRunner(console, "1.21.5").run(gameTestCase());

        assertTrue(result.passed(), () -> "应通过，附注: " + result.notes());
        assertEquals(List.of(DatapackSmokeRunner.COMMAND_DATAPACK_LIST, DatapackSmokeRunner.COMMAND_RELOAD,
                DatapackSmokeRunner.COMMAND_TEST_CREATE, DatapackSmokeRunner.COMMAND_TEST_LOCATE,
                "/function smoke:check", DatapackSmokeRunner.COMMAND_TEST_CLEARALL), result.commands());
        assertNull(result.notes());
    }

    @Test
    void gameTestFailureIsReportedFromTestResponse() {
        RecordingConsole console = new RecordingConsole(Map.of(
                DatapackSmokeRunner.COMMAND_DATAPACK_LIST, LIST_REPLY,
                DatapackSmokeRunner.COMMAND_RELOAD, "Reloaded 2 data packs",
                DatapackSmokeRunner.COMMAND_TEST_CREATE, "Test structure created at 0 4 0",
                DatapackSmokeRunner.COMMAND_TEST_LOCATE, "Nearest test structure at 0 4 0",
                "/function smoke:check", "Test failed: expected 3 but was 2",
                DatapackSmokeRunner.COMMAND_TEST_CLEARALL, "Cleared all test structures"));

        DatapackRunResult result = new DatapackSmokeRunner(console, "1.21.6").run(gameTestCase());

        assertFalse(result.passed());
        assertEquals("/function smoke:check", result.failedStep());
        assertTrue(result.commands().contains(DatapackSmokeRunner.COMMAND_TEST_CLEARALL),
                "失败也要清理测试结构");
    }

    @Test
    void consoleFailureDegradesWithChineseNote() {
        DatapackConsole broken = command -> {
            throw new IllegalStateException("连接已断开");
        };

        DatapackRunResult result = new DatapackSmokeRunner(broken, "1.21.4").run(endsWithCase());

        assertFalse(result.passed());
        assertEquals(DatapackSmokeRunner.COMMAND_DATAPACK_LIST, result.failedStep());
        assertTrue(result.notes().contains("控制台不可用"), "环境不具备要给出中文说明: " + result.notes());
    }

    @Test
    void gameTestResponseParsesPassFailAndCounts() {
        GameTestResponse passed = GameTestResponse.parse("Test structure created at 0 4 0\nTest passed!");
        assertTrue(passed.created());
        assertTrue(passed.passed());
        assertFalse(passed.failed());

        GameTestResponse failed = GameTestResponse.parse("Test failed: expected 3 but was 2");
        assertTrue(failed.failed());
        assertFalse(failed.passed());

        GameTestResponse counted = GameTestResponse.parse("1 test passed, 0 tests failed");
        assertTrue(counted.passed());
        assertFalse(counted.failed(), "0 tests failed 不能被当成失败");

        GameTestResponse cleared = GameTestResponse.parse("Cleared all test structures");
        assertTrue(cleared.cleared());
    }

    private static DatapackCase endsWithCase() {
        return new DatapackCase("smoke", "smoke", "check", "smoke",
                "Steve has 7 [laplace_smoke]", AssertBackend.ENDS_WITH);
    }

    private static DatapackCase gameTestCase() {
        return new DatapackCase("gametest", "smoke", "check", "smoke",
                "Steve has 7 [laplace_smoke]", AssertBackend.ASSERT_GAMETEST);
    }

    /** 记录命令顺序的内存控制台。 */
    private static final class RecordingConsole implements DatapackConsole {
        private final Map<String, String> replies;
        private final List<String> commands = new ArrayList<>();

        private RecordingConsole(Map<String, String> replies) {
            this.replies = new LinkedHashMap<>(replies);
        }

        @Override
        public String execute(String command) {
            commands.add(command);
            return replies.getOrDefault(command, "");
        }
    }
}
