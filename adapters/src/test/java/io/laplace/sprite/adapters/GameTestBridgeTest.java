package io.laplace.sprite.adapters;

import io.laplace.sprite.adapters.gametest.AssertionSeverity;
import io.laplace.sprite.adapters.gametest.GameTestCaseResult;
import io.laplace.sprite.adapters.gametest.GameTestBridge;
import io.laplace.sprite.adapters.gametest.GameTestSuites;
import io.laplace.sprite.adapters.gametest.NativeGameTestDeclaration;
import io.laplace.sprite.adapters.gametest.NativeGameTestResult;
import io.laplace.sprite.adapters.gametest.PlatformAssertion;
import io.laplace.sprite.adapters.gametest.PlatformAssertionScript;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.event.EventType;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GameTest 双向桥（14.6）：退出码语义、往返保真与事件发射。 */
class GameTestBridgeTest {

    private static final int FAILED_CASES = 2;

    /** 一段带日志前缀的 {@code /test runall} 真实形态输出：12 通过 2 失败，失败行后跟栈帧。 */
    private static final List<String> RUN_ALL = List.of(
            "[12:00:00] [Server thread/INFO]: Started test run: runall",
            "[12:00:01] [Server thread/INFO]: test.laplace.legit.WalkBridgeTest passed!",
            "[12:00:01] [Server thread/INFO]: test.laplace.legit.FallDamageTest passed!",
            "[12:00:01] [Server thread/INFO]: test.laplace.legit.ReachTest passed!",
            "[12:00:01] [Server thread/INFO]: test.laplace.legit.SprintJumpTest passed!",
            "[12:00:01] [Server thread/INFO]: test.laplace.legit.InventoryClickTest passed!",
            "[12:00:01] [Server thread/INFO]: test.laplace.legit.PacketOrderTest passed!",
            "[12:00:01] [Server thread/INFO]: test.laplace.legit.BedSleepTest passed!",
            "[12:00:01] [Server thread/INFO]: test.laplace.legit.TotemPopTest passed!",
            "[12:00:01] [Server thread/INFO]: test.laplace.legit.ElytraGlideTest passed!",
            "[12:00:01] [Server thread/INFO]: test.laplace.legit.VehicleEnterTest passed!",
            "[12:00:01] [Server thread/INFO]: test.laplace.legit.BlockPlaceTest passed!",
            "[12:00:01] [Server thread/INFO]: test.laplace.legit.ChunkLoadTest passed!",
            "[12:00:02] [Server thread/INFO]: test.laplace.cheat.KillAuraFlagTest failed! expected flag within 20 ticks",
            "\tat net.minecraft.gametest.framework.GameTestHelper.fail(GameTestHelper.java:312)",
            "[12:00:02] [Server thread/INFO]: test.laplace.cheat.ReachFlagTest failed! reach 4.7 exceeded 3.0",
            "[12:00:02] [Server thread/INFO]: 12 tests passed, 2 tests failed.");

    @Test
    void parsesRunAllOutputAndKeepsTheExitCodeConsistent() {
        NativeGameTestResult result = GameTestSuites.fromNative(FAILED_CASES, RUN_ALL);

        assertEquals(14, result.total());
        assertEquals(FAILED_CASES, result.failed());
        assertEquals(FAILED_CASES, result.exitCode(), "退出码就是失败用例数");
        assertEquals(14, result.cases().size());
        assertFalse(result.passed());

        GameTestCaseResult firstFailure = result.cases().get(12);
        assertEquals("test.laplace.cheat.KillAuraFlagTest", firstFailure.name());
        assertEquals("expected flag within 20 ticks", firstFailure.detail());
        assertTrue(result.cases().stream().filter(GameTestCaseResult::passed)
                .allMatch(test -> test.detail().isEmpty()), "通过用例不打印断言表达式");
    }

    @Test
    void rejectsAnExitCodeThatContradictsTheLog() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> GameTestSuites.fromNative(1, RUN_ALL));

        assertTrue(error.getMessage().contains("退出码"), error.getMessage());
    }

    @Test
    void nativeResultEnforcesExitCodeEqualsFailedCount() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new NativeGameTestResult(14, FAILED_CASES, 3, List.of()));

        assertTrue(error.getMessage().contains("失败用例数"), error.getMessage());
    }

    @Test
    void platformScriptSurvivesTheRoundTrip() {
        PlatformAssertionScript script = new PlatformAssertionScript("legit-suite", List.of(
                new PlatformAssertion("walk-bridge", "player.blocksWalked() == 12", AssertionSeverity.PASS),
                new PlatformAssertion("reach", "player.reach() <= 3.0", AssertionSeverity.FAIL),
                new PlatformAssertion("totem", "player.totemPopped()", AssertionSeverity.WARN)));
        GameTestBridge bridge = new GameTestBridge("gametest-bridge");

        assertTrue(bridge.isRoundTripStable(script), "名字 / 期望 / 通过与否必须往返保真");

        NativeGameTestDeclaration declaration = bridge.toNative(script);
        assertEquals("legit-suite", declaration.suite());
        assertEquals(3, declaration.tests().size());
        assertEquals("player.reach() <= 3.0", declaration.tests().get(1).assertion());
        assertTrue(declaration.tests().get(1).failed());
        assertFalse(declaration.tests().get(2).failed(), "WARN 在原生侧按未失败处理");
        assertEquals(1, declaration.asResult().failed());
        assertEquals(1, declaration.asResult().exitCode());
    }

    @Test
    void severityLevelIsTheOnlyFieldThatDegradesAcrossTheRoundTrip() {
        GameTestBridge bridge = new GameTestBridge();
        PlatformAssertionScript script = new PlatformAssertionScript("suite", List.of(
                new PlatformAssertion("a", "x", AssertionSeverity.WARN)));

        assertTrue(bridge.isRoundTripStable(script), "只声明名字 / 期望 / 通过与否三项保真");

        PlatformAssertion roundTripped = bridge.toPlatform(bridge.toNative(script).asResult())
                .assertions().get(0);
        assertEquals(AssertionSeverity.PASS, roundTripped.severity(),
                "WARN 在原生侧没有对应态，往返后降级为 PASS");
    }

    @Test
    void nativeToPlatformKeepsNameAndVerdictAndDropsTheAssertionText() {
        GameTestBridge bridge = new GameTestBridge();

        PlatformAssertionScript script = bridge.toPlatform(GameTestSuites.fromNative(FAILED_CASES, RUN_ALL));

        assertEquals(14, script.size());
        assertEquals(FAILED_CASES, script.failedCount());
        PlatformAssertion passed = script.assertions().get(0);
        assertEquals("test.laplace.legit.WalkBridgeTest", passed.name());
        assertEquals("", passed.expectation(), "原生输出不带通过用例的断言表达式");
        PlatformAssertion failed = script.assertions().stream()
                .filter(PlatformAssertion::failed).findFirst().orElseThrow();
        assertEquals("expected flag within 20 ticks", failed.expectation());
        assertEquals(AssertionSeverity.FAIL, failed.severity());
    }

    @Test
    void nativeRunLandsInTheEventStream() {
        GameTestBridge bridge = new GameTestBridge("runall");
        PlatformKey key = PlatformKey.of(PlatformFamily.NEOFORGE, "1.21.11");

        List<Event.GameTestSuite> events = bridge.toEvents(GameTestSuites.fromNative(FAILED_CASES, RUN_ALL), key);

        assertEquals(14, events.size());
        assertEquals(FAILED_CASES, events.stream().filter(event -> !event.passed()).count());
        assertEquals(EventType.GAME_TEST_SUITE, events.get(0).type());
        assertEquals("runall", events.get(0).suite());
        assertEquals(key, events.get(0).platform());
        assertNull(events.get(0).failure());
        assertNotNull(events.stream().filter(event -> !event.passed()).findFirst().orElseThrow().failure());
    }
}
