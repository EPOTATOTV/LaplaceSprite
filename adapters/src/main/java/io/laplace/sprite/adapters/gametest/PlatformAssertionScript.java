package io.laplace.sprite.adapters.gametest;

import java.util.List;
import java.util.Objects;

/**
 * 平台断言脚本（14.6）：一组平台无关断言，是原生 GameTest 与平台断言之间的公共表示。
 *
 * @param id         脚本 / 套件标识
 * @param assertions 断言列表，顺序即执行顺序
 */
public record PlatformAssertionScript(String id, List<PlatformAssertion> assertions) {

    public PlatformAssertionScript {
        Objects.requireNonNull(id, "id");
        assertions = assertions == null ? List.of() : List.copyOf(assertions);
    }

    public int size() {
        return assertions.size();
    }

    public long failedCount() {
        return assertions.stream().filter(PlatformAssertion::failed).count();
    }
}
