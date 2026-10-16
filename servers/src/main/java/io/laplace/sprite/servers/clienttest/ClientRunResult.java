package io.laplace.sprite.servers.clienttest;

import java.nio.file.Path;

/**
 * 一次客户端用例的执行结果（15.7 证据三件套的原始形态）。
 *
 * @param caseId         用例 id
 * @param passed         用例是否通过；截图缺失这条规则由执行器补判
 * @param failedStep     失败步骤描述，通过时为 null
 * @param logsPath       客户端日志
 * @param resultsXmlPath test-results XML
 * @param screenshotPath 客户端截图；为空即判失败
 * @param notes          附注
 */
public record ClientRunResult(String caseId, boolean passed, String failedStep, Path logsPath,
                              Path resultsXmlPath, Path screenshotPath, String notes) {

    public static ClientRunResult passed(String caseId, Path logs, Path resultsXml, Path screenshot) {
        return new ClientRunResult(caseId, true, null, logs, resultsXml, screenshot, null);
    }

    public static ClientRunResult failed(String caseId, String failedStep, Path logs, Path resultsXml,
                                         Path screenshot, String notes) {
        return new ClientRunResult(caseId, false, failedStep, logs, resultsXml, screenshot, notes);
    }
}
