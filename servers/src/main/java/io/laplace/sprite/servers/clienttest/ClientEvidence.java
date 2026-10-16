package io.laplace.sprite.servers.clienttest;

import java.nio.file.Path;

/**
 * 客户端用例证据（15.7）：失败必传 logs、test-results XML、客户端截图。
 *
 * @param caseId         用例 id
 * @param logsPath       客户端日志
 * @param resultsXmlPath test-results XML
 * @param screenshotPath 客户端截图
 * @param passed         判定后的结论
 * @param failedStep     失败步骤描述，通过时为 null
 * @param notes          附注
 */
public record ClientEvidence(String caseId, Path logsPath, Path resultsXmlPath, Path screenshotPath,
                             boolean passed, String failedStep, String notes) {

    /** 三件套是否齐全。 */
    public boolean complete() {
        return logsPath != null && resultsXmlPath != null && screenshotPath != null;
    }
}
