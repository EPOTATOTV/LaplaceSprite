package io.laplace.sprite.adapters.gametest;

import io.laplace.sprite.adapters.event.SyntheticStreams;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.PlatformKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * GameTest 双向桥（14.6）：原生 GameTest 与平台断言脚本互转。
 *
 * <p>往返保真的字段（platform → native → platform）：用例名、期望（断言表达式）、通过与否。
 * 有损的部分：JUnit 风格的异常栈帧只留首行摘要；native → platform 方向不携带通过用例的断言表达式
 * （控制台只打印 passed，不打印断言逻辑）；{@code WARN} 在原生侧没有对应态，落到声明后按未失败处理，
 * 往返回来会变成 {@code PASS}；单用例耗时、结构模板路径、test_environment 定义与随机种子两个方向
 * 都不保留。因此只声明这三项可往返，不声称完整保真。</p>
 */
public final class GameTestBridge {

    public static final String DEFAULT_SUITE = "native-gametest";

    private final String suite;

    public GameTestBridge() {
        this(DEFAULT_SUITE);
    }

    public GameTestBridge(String suite) {
        this.suite = Objects.requireNonNull(suite, "suite");
    }

    /** 按加载器取桥：套件名带上加载器，失败时能看出是哪一家的 GameTest 输出的。 */
    public static GameTestBridge forLoader(GameTestLoader loader) {
        Objects.requireNonNull(loader, "loader");
        return new GameTestBridge(loader.suite());
    }

    /** 原生运行结果 → 平台断言脚本。 */
    public PlatformAssertionScript toPlatform(NativeGameTestResult result) {
        List<PlatformAssertion> assertions = new ArrayList<>(result.cases().size());
        for (GameTestCaseResult test : result.cases()) {
            assertions.add(new PlatformAssertion(test.name(), test.detail(),
                    AssertionSeverity.of(test.passed())));
        }
        return new PlatformAssertionScript(suite, assertions);
    }

    /** 平台断言脚本 → 原生风格的用例声明。 */
    public NativeGameTestDeclaration toNative(PlatformAssertionScript script) {
        List<NativeGameTest> tests = new ArrayList<>(script.size());
        for (PlatformAssertion assertion : script.assertions()) {
            tests.add(new NativeGameTest(assertion.name(), assertion.expectation(), assertion.failed()));
        }
        return new NativeGameTestDeclaration(script.id(), tests);
    }

    /** 往返校验：脚本 → 原生声明 → 原生结果 → 脚本，逐条比对三项保真字段。 */
    public boolean isRoundTripStable(PlatformAssertionScript script) {
        PlatformAssertionScript again = toPlatform(toNative(script).asResult());
        if (again.size() != script.size()) {
            return false;
        }
        for (int i = 0; i < script.size(); i++) {
            PlatformAssertion before = script.assertions().get(i);
            PlatformAssertion after = again.assertions().get(i);
            if (!before.name().equals(after.name())
                    || !before.expectation().equals(after.expectation())
                    || before.failed() != after.failed()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 把一次原生运行落进平台事件流：一条用例一个 {@link Event.GameTestSuite}。
     *
     * <p>不挂进 EventBridges：那是「按家族取桥」的工厂，而原生 GameTest 结果是从控制台拿到的
     * 离线产物，不属于某个家族的桥生命周期。GameTest 是服务端断言、没有玩家主体，
     * 玩家标识沿用合成流的占位值。</p>
     */
    public List<Event.GameTestSuite> toEvents(NativeGameTestResult result, PlatformKey platform) {
        List<Event.GameTestSuite> events = new ArrayList<>(result.cases().size());
        for (GameTestCaseResult test : result.cases()) {
            long tick = events.size();
            events.add(new Event.GameTestSuite(SyntheticStreams.PLAYER, tick, SyntheticStreams.timestamp(tick),
                    platform, suite, test.name(), test.passed(), test.passed() ? null : test.detail()));
        }
        return List.copyOf(events);
    }

    public String suite() {
        return suite;
    }
}
