package io.laplace.sprite.core.spi;

import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.model.AssertionResult;
import io.laplace.sprite.core.model.Scenario;

import java.util.List;

/**
 * ⑤ 断言契约（14.4）：平台无关。断言只读内部事件流与性能指标，不感知具体平台。
 */
public interface AssertionContract {

    List<AssertionResult> run(Scenario scenario, EventStream stream);
}
