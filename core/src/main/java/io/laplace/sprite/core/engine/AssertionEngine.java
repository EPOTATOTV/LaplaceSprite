package io.laplace.sprite.core.engine;

import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.model.AssertionResult;
import io.laplace.sprite.core.model.Scenario;
import io.laplace.sprite.core.spi.AssertionContract;
import io.laplace.sprite.core.spi.PlatformAgnosticAssertionContract;

import java.util.List;

/**
 * 断言引擎（4.5 判定层）：平台无关，只读内部事件流。
 */
public final class AssertionEngine {

    private final AssertionContract contract;

    public AssertionEngine() {
        this(new PlatformAgnosticAssertionContract());
    }

    public AssertionEngine(AssertionContract contract) {
        this.contract = contract;
    }

    public List<AssertionResult> evaluate(Scenario scenario, EventStream stream) {
        return contract.run(scenario, stream);
    }

    public static boolean allPassed(List<AssertionResult> results) {
        return results.stream().noneMatch(AssertionResult::failed);
    }
}
