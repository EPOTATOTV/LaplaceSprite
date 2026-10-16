package io.laplace.sprite.cli;

import io.laplace.sprite.servers.clienttest.ClientEvidence;
import io.laplace.sprite.servers.clienttest.ClientTestMatrix;
import io.laplace.sprite.servers.clienttest.ClientTestRunner;
import io.laplace.sprite.servers.clienttest.ClientTestSuite;
import io.laplace.sprite.servers.clienttest.ClientTestSuiteLoader;
import io.laplace.sprite.servers.clienttest.ClientTestSuiteResult;
import io.laplace.sprite.servers.clienttest.LoomTaskRenderer;
import io.laplace.sprite.servers.clienttest.ScriptedClientHarness;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * {@code laplace clienttest}：15.7 CI 客户端测试轨。
 *
 * <p>离线用脚本替身推演：SEND 的效果来自命令表，截图只能拷贝调用方给的真实图片。
 * 缺截图按失败处理——「失败必传截图」是硬规则，不是可选项。</p>
 */
@Command(name = "clienttest",
        description = "CI 客户端测试轨：声明式用例 DSL、证据三件套、XVFB 与 Loom 任务渲染",
        mixinStandardHelpOptions = true)
final class ClientTestCommand implements Callable<Integer> {

    /** 内置冒烟套件里 SEND 命令的离线回显：命令 → 日志尾部出现的行。 */
    private static final Map<String, String> COMMAND_EFFECTS = Map.of(
            "minecraft:reload", "[Server] Reloaded 3 data packs");

    @Option(names = "--suite", description = "用例套件 JSON；缺省用内置冒烟套件")
    Path suite;

    @Option(names = "--screenshot", description = "真实截图文件；不给则截图步骤判失败")
    Path screenshot;

    @Option(names = "--out", description = "产物目录，默认 <workspace>/clienttest")
    Path out;

    @Option(names = "--workspace", description = "工作目录，默认 build/laplace")
    Path workspace;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        Path dir = out != null ? out : Support.workspace(workspace).resolve("clienttest");
        var writer = spec.commandLine().getOut();

        ClientTestSuiteLoader loader = new ClientTestSuiteLoader();
        ClientTestSuite loaded = suite != null ? loader.load(suite) : loader.loadBundledSmokeSuite();
        ClientTestRunner runner = new ClientTestRunner(
                new ScriptedClientHarness(dir.resolve("artifacts"), COMMAND_EFFECTS, screenshot));
        ClientTestSuiteResult result = runner.run(loaded);

        writer.println("CI 客户端测试轨（15.7） · 套件 " + loaded.name() + "（" + loaded.id() + "）");
        writer.println("  离线替身执行；真实运行需 Linux runner + XVFB + Loom runClientGameTest");
        for (ClientEvidence evidence : result.evidences()) {
            writer.printf("    %-14s 通过=%-5s 失败步骤=%s 证据完整=%s%n", evidence.caseId(), evidence.passed(),
                    evidence.failedStep() == null ? "—" : evidence.failedStep(), evidence.complete());
            writer.println("      logs=" + evidence.logsPath() + " xml=" + evidence.resultsXmlPath()
                    + " screenshot=" + evidence.screenshotPath());
        }
        writer.println("  合计 通过 " + result.passed() + " / 失败 " + result.failed());
        result.syncWorkaroundProperty().ifPresent(property ->
                writer.println("  网络同步器报错 → 追加 " + property));

        ClientTestMatrix matrix = ClientTestMatrix.defaults();
        writer.println("  多维矩阵 " + matrix.size() + " 格（MC × 加载器 × JDK × OS）· XVFB 自动启用="
                + matrix.useXvfb());
        writer.println("  Loom 任务 " + new LoomTaskRenderer().renderLine(
                result.syncWorkaroundProperty().isPresent(), matrix.useXvfb()));

        return result.allPassed() ? 0 : 1;
    }
}
