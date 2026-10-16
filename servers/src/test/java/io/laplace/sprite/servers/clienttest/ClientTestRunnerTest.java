package io.laplace.sprite.servers.clienttest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 客户端套件执行器（15.7）：证据三件套、截图必传、网络同步器兜底。 */
class ClientTestRunnerTest {

    private static final Map<String, String> RELOAD_EFFECTS =
            Map.of("minecraft:reload", "Reloaded 3 data packs");

    @Test
    void passingSuiteProducesEvidenceTrio(@TempDir Path dir) throws IOException {
        Path screenshotSource = writeFakeScreenshot(dir);
        ScriptedClientHarness harness = new ScriptedClientHarness(dir.resolve("artifacts"), RELOAD_EFFECTS,
                screenshotSource);

        ClientTestSuiteResult result = new ClientTestRunner(harness)
                .run(new ClientTestSuiteLoader().loadBundledSmokeSuite());

        assertEquals(2, result.passed());
        assertEquals(0, result.failed());
        assertTrue(result.allPassed());
        assertFalse(result.syncWorkaroundRequired());
        assertTrue(result.syncWorkaroundProperty().isEmpty());

        ClientEvidence evidence = result.evidences().get(0);
        assertTrue(evidence.passed());
        assertNull(evidence.failedStep());
        assertTrue(evidence.complete(), "通过用例应带齐三件套");
        assertTrue(Files.exists(evidence.logsPath()));
        assertTrue(Files.exists(evidence.resultsXmlPath()));
        assertTrue(Files.exists(evidence.screenshotPath()));
    }

    @Test
    void failingStepIsReported(@TempDir Path dir) throws IOException {
        ScriptedClientHarness harness = new ScriptedClientHarness(dir.resolve("artifacts"),
                Map.of(), writeFakeScreenshot(dir));
        ClientTestCase testCase = new ClientTestCase("wrong-assert", "断言写错了", "1.21.1", "fabric", "21",
                "linux", List.of(ClientDslStep.of(ClientDslOp.SEND, "minecraft:reload"),
                        ClientDslStep.of(ClientDslOp.EXPECT_LOG_ENDS_WITH, "这一行不会出现"),
                        ClientDslStep.of(ClientDslOp.SCREENSHOT, null)));

        ClientEvidence evidence = new ClientTestRunner(harness).runCase(testCase);

        assertFalse(evidence.passed());
        assertEquals("EXPECT_LOG_ENDS_WITH 这一行不会出现", evidence.failedStep());
        assertTrue(evidence.notes().contains("断言未通过"));
    }

    @Test
    void missingScreenshotTurnsPassingCaseIntoFailure(@TempDir Path dir) throws IOException {
        // 离线不具备截图能力：DSL 断言全过也不许算通过
        ScriptedClientHarness harness = new ScriptedClientHarness(dir.resolve("artifacts"), RELOAD_EFFECTS,
                null);
        ClientTestCase testCase = new ClientTestCase("no-shot", "没有截图", "1.21.1", "fabric", "21", "linux",
                List.of(ClientDslStep.of(ClientDslOp.SEND, "minecraft:reload"),
                        ClientDslStep.of(ClientDslOp.EXPECT_LOG_ENDS_WITH, "Reloaded 3 data packs"),
                        ClientDslStep.of(ClientDslOp.SCREENSHOT, null)));

        ClientEvidence evidence = new ClientTestRunner(harness).runCase(testCase);

        assertFalse(evidence.passed());
        assertEquals(ClientDslOp.SCREENSHOT.name(), evidence.failedStep());
        assertNull(evidence.screenshotPath());
        assertTrue(evidence.notes().contains("失败必传截图"), evidence.notes());
        assertTrue(evidence.notes().contains("证据三件套不完整"), evidence.notes());
    }

    @Test
    void networkSynchronizerErrorRequiresWorkaroundFlag(@TempDir Path dir) {
        ScriptedClientHarness harness = new ScriptedClientHarness(dir.resolve("artifacts"),
                Map.of("laplace:probe",
                        "java.lang.IllegalStateException: Network synchronizer failed after 30s"), null);
        ClientTestCase testCase = new ClientTestCase("sync", "同步器报错", "1.21.1", "fabric", "21", "linux",
                List.of(ClientDslStep.of(ClientDslOp.SEND, "laplace:probe"),
                        ClientDslStep.of(ClientDslOp.EXPECT_LOG_ENDS_WITH, "Network synchronizer failed")));

        ClientTestSuiteResult result = new ClientTestRunner(harness)
                .run(new ClientTestSuite("sync-suite", "同步器", List.of(testCase)));

        assertTrue(result.syncWorkaroundRequired(), "日志出现网络同步器报错时必须标记兜底参数");
        assertEquals(LoomTaskRenderer.SYNC_WORKAROUND_PROPERTY, result.syncWorkaroundProperty().orElseThrow());
    }

    /** 夹具用的“截图”：只是拷贝源，不是真实渲染产物。 */
    private static Path writeFakeScreenshot(Path dir) throws IOException {
        Path source = dir.resolve("screenshot-source.png");
        Files.writeString(source, "fixture-not-a-real-screenshot", StandardCharsets.UTF_8);
        return source;
    }
}
