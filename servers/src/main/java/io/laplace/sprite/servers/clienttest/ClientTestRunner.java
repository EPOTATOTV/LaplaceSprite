package io.laplace.sprite.servers.clienttest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 客户端套件执行器（15.7）：逐用例走 {@link ClientHarness}，再套上证据三件套与网络同步器兜底规则。
 *
 * <p>两条规则在这里落地：截图缺失一律判失败（最小断言单元就是「进世界 → 等渲染完成 → 截图」）；
 * 日志里出现网络同步器报错时，结果标记需要追加 {@value LoomTaskRenderer#SYNC_WORKAROUND_PROPERTY}。</p>
 */
public final class ClientTestRunner {

    /** Fabric 客户端游戏测试里网络同步器报错的特征串。 */
    private static final String SYNC_ERROR_MARKER = "network synchronizer";

    private final ClientHarness harness;

    public ClientTestRunner(ClientHarness harness) {
        this.harness = harness;
    }

    public ClientTestSuiteResult run(ClientTestSuite suite) {
        List<ClientEvidence> evidences = new ArrayList<>(suite.cases().size());
        boolean syncWorkaround = false;
        for (ClientTestCase testCase : suite.cases()) {
            ClientRunResult result = harness.run(testCase);
            evidences.add(evidence(testCase, result));
            syncWorkaround = syncWorkaround || syncWorkaroundNeeded(result);
        }
        int passed = (int) evidences.stream().filter(ClientEvidence::passed).count();
        return new ClientTestSuiteResult(suite.id(), evidences, passed, evidences.size() - passed,
                syncWorkaround);
    }

    /** 单用例执行：命令行按用例取证据时用。 */
    public ClientEvidence runCase(ClientTestCase testCase) {
        return evidence(testCase, harness.run(testCase));
    }

    private static ClientEvidence evidence(ClientTestCase testCase, ClientRunResult result) {
        boolean passed = result.passed();
        String failedStep = result.failedStep();
        String notes = result.notes();
        if (passed && result.screenshotPath() == null) {
            passed = false;
            failedStep = ClientDslOp.SCREENSHOT.name();
            notes = append(notes, "失败必传截图：本次未产出客户端截图（15.7 证据三件套）");
        }
        ClientEvidence evidence = new ClientEvidence(testCase.id(), result.logsPath(),
                result.resultsXmlPath(), result.screenshotPath(), passed, failedStep, notes);
        if (!evidence.passed() && !evidence.complete()) {
            return new ClientEvidence(testCase.id(), result.logsPath(), result.resultsXmlPath(),
                    result.screenshotPath(), false, failedStep,
                    append(notes, "失败用例证据三件套不完整（logs / test-results XML / 截图）"));
        }
        return evidence;
    }

    private static boolean syncWorkaroundNeeded(ClientRunResult result) {
        if (result.logsPath() == null) {
            return false;
        }
        try {
            String logs = Files.readString(result.logsPath(), StandardCharsets.UTF_8);
            return logs.toLowerCase(Locale.ROOT).contains(SYNC_ERROR_MARKER);
        } catch (IOException ex) {
            return false;
        }
    }

    private static String append(String existing, String note) {
        return existing == null || existing.isBlank() ? note : existing + "；" + note;
    }
}
