package io.laplace.sprite.servers.clienttest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 离线客户端替身（15.7）：没有真实客户端与 Loom 时，按用例步骤在内存里推演日志与产物。
 *
 * <p>SEND 的效果来自 {@code commandEffects}（命令 → 该命令让日志尾部出现的那一行）。截图只能拷贝调用方
 * 给出的真实图片，没给就不产出——缺截图由执行器按失败处理，这里不伪造任何图像。</p>
 */
public final class ScriptedClientHarness implements ClientHarness {

    private static final String ENTER_WORLD_MARKER = "[LaplaceClient] 已进入世界: ";
    private static final String RENDER_DONE_MARKER = "[Render] 首帧渲染完成";
    private static final String ECHO_PREFIX = "[Server] > ";

    private final Path artifactDir;
    private final Map<String, String> commandEffects;
    private final Path screenshotSource;

    /**
     * @param artifactDir      logs / test-results / screenshots 的落点
     * @param commandEffects   SEND 命令 → 命令执行后日志尾部出现的行
     * @param screenshotSource 真实截图文件；给 null 表示当前不具备截图能力
     */
    public ScriptedClientHarness(Path artifactDir, Map<String, String> commandEffects, Path screenshotSource) {
        this.artifactDir = artifactDir;
        this.commandEffects = Map.copyOf(commandEffects);
        this.screenshotSource = screenshotSource;
    }

    @Override
    public ClientRunResult run(ClientTestCase testCase) {
        List<String> logs = new ArrayList<>();
        logs.add(ENTER_WORLD_MARKER + testCase.id());
        Path screenshot = null;
        String failedStep = null;

        for (ClientDslStep step : testCase.steps()) {
            switch (step.op()) {
                case SEND -> logs.add(commandEffects.getOrDefault(step.value(), ECHO_PREFIX + step.value()));
                case EXPECT_LOG_ENDS_WITH -> {
                    if (logs.isEmpty() || !logs.get(logs.size() - 1).endsWith(step.value())) {
                        failedStep = step.describe();
                    }
                }
                // 离线不推进真实 tick：等待时长只对真实客户端有意义
                case WAIT_TICKS -> logs.add("[Wait] " + step.value() + " ticks");
                case WAIT_FOR_RENDER -> logs.add(RENDER_DONE_MARKER);
                case SCREENSHOT -> screenshot = copyScreenshot(testCase);
            }
            if (failedStep != null) {
                break;
            }
        }

        Path logsPath = writeLogs(testCase, logs);
        Path resultsXml = writeResultsXml(testCase, failedStep);
        if (failedStep == null) {
            return new ClientRunResult(testCase.id(), true, null, logsPath, resultsXml, screenshot, null);
        }
        return ClientRunResult.failed(testCase.id(), failedStep, logsPath, resultsXml, screenshot, "断言未通过");
    }

    private Path writeLogs(ClientTestCase testCase, List<String> logs) {
        Path target = artifactDir.resolve("logs").resolve(testCase.id() + ".log");
        write(target, String.join(System.lineSeparator(), logs) + System.lineSeparator());
        return target;
    }

    private Path writeResultsXml(ClientTestCase testCase, String failedStep) {
        Path target = artifactDir.resolve("test-results").resolve(testCase.id() + ".xml");
        boolean failed = failedStep != null;
        String failure = failed ? "    <failure message=\"" + escape(failedStep) + "\"/>\n" : "";
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="%s" tests="1" failures="%d">
                  <testcase name="%s" classname="clienttest">
                %s  </testcase>
                </testsuite>
                """.formatted(testCase.id(), failed ? 1 : 0, escape(testCase.name()), failure);
        write(target, xml);
        return target;
    }

    private Path copyScreenshot(ClientTestCase testCase) {
        if (screenshotSource == null || !Files.isRegularFile(screenshotSource)) {
            return null;
        }
        Path target = artifactDir.resolve("screenshots").resolve(testCase.id() + ".png");
        try {
            Files.createDirectories(target.getParent());
            Files.copy(screenshotSource, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return target;
    }

    private static void write(Path target, String content) {
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static String escape(String raw) {
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
