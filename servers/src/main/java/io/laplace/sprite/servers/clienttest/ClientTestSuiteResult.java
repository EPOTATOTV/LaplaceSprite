package io.laplace.sprite.servers.clienttest;

import java.util.List;
import java.util.Optional;

/**
 * 一次套件执行结果（15.7）。
 *
 * @param suiteId                套件 id
 * @param evidences              逐用例证据
 * @param passed                 通过数
 * @param failed                 失败数
 * @param syncWorkaroundRequired 是否需要追加网络同步器兜底参数
 */
public record ClientTestSuiteResult(String suiteId, List<ClientEvidence> evidences, int passed, int failed,
                                    boolean syncWorkaroundRequired) {

    public ClientTestSuiteResult {
        evidences = evidences == null ? List.of() : List.copyOf(evidences);
    }

    /** 套件是否整体通过：无失败用例且至少跑了一个。 */
    public boolean allPassed() {
        return failed == 0 && !evidences.isEmpty();
    }

    /** 需要追加的 JVM 参数，不需要时为空。 */
    public Optional<String> syncWorkaroundProperty() {
        return syncWorkaroundRequired
                ? Optional.of(LoomTaskRenderer.SYNC_WORKAROUND_PROPERTY)
                : Optional.empty();
    }
}
