package io.laplace.sprite.datapack;

import io.laplace.sprite.core.spi.VersionOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据包冒烟执行器（15.8）：按文档给定的顺序下发命令并断言。
 *
 * <p>顺序固定为 {@code /datapack list enabled} → {@code /reload} →
 * {@code /function <命名空间>:<用例函数>}。Game Test 后端在 function 前后插入
 * {@code /test create|locate|clearall}，但不改变这三条命令的相对次序。</p>
 */
public final class DatapackSmokeRunner {

    public static final String COMMAND_DATAPACK_LIST = "/datapack list enabled";
    public static final String COMMAND_RELOAD = "/reload";
    public static final String COMMAND_TEST_CREATE = "/test create";
    public static final String COMMAND_TEST_LOCATE = "/test locate";
    public static final String COMMAND_TEST_CLEARALL = "/test clearall";

    /** Game Test 接入数据包的起始版本：1.21.5。 */
    public static final String GAMETEST_MIN_VERSION = "1.21.5";

    private static final Pattern ENABLED_ENTRY = Pattern.compile("\\[([^]]+)]");
    private static final Pattern SCOREBOARD_LINE =
            Pattern.compile("^\\s*\\S.*\\shas\\s+-?\\d+\\s+\\[[^]]+]$");
    private static final List<String> RELOAD_FAILURE_MARKERS =
            List.of("failed to reload", "reload failed", "error reload", "exception", "couldn't",
                    "could not");

    private final DatapackConsole console;
    private final String serverVersion;

    public DatapackSmokeRunner(DatapackConsole console, String serverVersion) {
        this.console = Objects.requireNonNull(console, "console");
        if (serverVersion == null || serverVersion.isBlank()) {
            throw new IllegalArgumentException("服务端版本不能为空");
        }
        this.serverVersion = serverVersion;
    }

    public String serverVersion() {
        return serverVersion;
    }

    /** 1.21.5 起 Game Test 系统接入数据包，低版本没有 {@code /test}。 */
    public boolean supportsGameTest() {
        return VersionOrder.compare(serverVersion, GAMETEST_MIN_VERSION) >= 0;
    }

    public DatapackRunResult run(DatapackCase testCase) {
        List<DatapackStep> steps = new ArrayList<>();
        List<String> notes = new ArrayList<>();

        AssertBackend backend = testCase.assertBackend();
        // 版本门控：低版本没有 /test，回退到日志断言，并在结果里如实记录回退
        if (backend == AssertBackend.ASSERT_GAMETEST && !supportsGameTest()) {
            backend = AssertBackend.ENDS_WITH;
            notes.add("服务端 %s < %s，ASSERT_GAMETEST 回退为 ENDS_WITH 日志断言"
                    .formatted(serverVersion, GAMETEST_MIN_VERSION));
        }

        String listReply = execute(COMMAND_DATAPACK_LIST, steps, notes);
        if (listReply == null) {
            return result(testCase, steps, false, null, null, false, COMMAND_DATAPACK_LIST, notes);
        }
        boolean enabled = enabled(listReply, testCase.packName());
        if (!enabled) {
            notes.add("目标数据包 %s 未出现在 /datapack list enabled 的回显里".formatted(testCase.packName()));
            return result(testCase, steps, false, null, null, false, COMMAND_DATAPACK_LIST, notes);
        }

        String reloadReply = execute(COMMAND_RELOAD, steps, notes);
        if (reloadReply == null) {
            return result(testCase, steps, true, null, null, false, COMMAND_RELOAD, notes);
        }
        if (reloadReply.isBlank()) {
            notes.add("/reload 无回显，按未报错处理");
        } else if (reloadFailed(reloadReply)) {
            notes.add("/reload 回显报错：" + reloadReply.trim());
            return result(testCase, steps, true, null, null, false, COMMAND_RELOAD, notes);
        }

        if (backend == AssertBackend.ASSERT_GAMETEST && !prepareGameTest(steps, notes)) {
            return result(testCase, steps, true, null, null, false, COMMAND_TEST_CREATE, notes);
        }

        String functionCommand = "/function " + testCase.functionReference();
        String functionOutput = execute(functionCommand, steps, notes);
        if (functionOutput == null) {
            return result(testCase, steps, true, null, null, false, functionCommand, notes);
        }
        String scoreboard = scoreboard(functionOutput);

        boolean passed;
        if (backend == AssertBackend.ASSERT_GAMETEST) {
            GameTestResponse response = GameTestResponse.parse(functionOutput);
            passed = response.passed() && !response.failed();
            if (!passed) {
                notes.add("Game Test 回包未判通过：" + abbreviate(functionOutput));
            }
        } else {
            passed = endsWithLastLine(functionOutput, testCase.expectedLogSuffix());
            if (!passed) {
                notes.add("函数回显最后一行未以「%s」结尾".formatted(testCase.expectedLogSuffix()));
            }
        }

        if (backend == AssertBackend.ASSERT_GAMETEST) {
            // 清理失败不改判用例：测试结构残留只影响下一个用例，记进附注
            String cleared = execute(COMMAND_TEST_CLEARALL, steps, notes);
            if (cleared != null && !GameTestResponse.parse(cleared).cleared()) {
                notes.add("/test clearall 未确认清理：" + abbreviate(cleared));
            }
        }

        return result(testCase, steps, true, functionOutput, scoreboard, passed,
                passed ? null : functionCommand, notes);
    }

    /** 搭测试结构：create 与 locate 都回显失败才算失败。 */
    private boolean prepareGameTest(List<DatapackStep> steps, List<String> notes) {
        String createReply = execute(COMMAND_TEST_CREATE, steps, notes);
        if (createReply == null) {
            return false;
        }
        String locateReply = execute(COMMAND_TEST_LOCATE, steps, notes);
        GameTestResponse response = GameTestResponse.parse(createReply + "\n"
                + (locateReply == null ? "" : locateReply));
        if (!response.created() && !response.located()) {
            notes.add("未搭起测试结构：/test create 回显为「" + abbreviate(createReply) + "」");
            return false;
        }
        return true;
    }

    private String execute(String command, List<DatapackStep> steps, List<String> notes) {
        try {
            String reply = console.execute(command);
            steps.add(new DatapackStep(command, reply));
            return reply == null ? "" : reply;
        } catch (RuntimeException ex) {
            steps.add(new DatapackStep(command, ""));
            notes.add("控制台不可用：%s 执行失败（%s）".formatted(command, ex.getMessage()));
            return null;
        }
    }

    private static boolean enabled(String reply, String packName) {
        Matcher matcher = ENABLED_ENTRY.matcher(reply);
        boolean sawEntry = false;
        while (matcher.find()) {
            sawEntry = true;
            String entry = matcher.group(1).trim();
            int descriptor = entry.indexOf(" (");
            String name = (descriptor < 0 ? entry : entry.substring(0, descriptor)).trim();
            if (name.equals(packName) || name.endsWith("/" + packName)) {
                return true;
            }
        }
        return !sawEntry && reply.contains(packName);
    }

    private static boolean reloadFailed(String reply) {
        String lower = reply.toLowerCase(Locale.ROOT);
        return RELOAD_FAILURE_MARKERS.stream().anyMatch(lower::contains);
    }

    private static boolean endsWithLastLine(String output, String suffix) {
        if (suffix == null || suffix.isEmpty()) {
            return true;
        }
        String[] lines = output.stripTrailing().split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            if (!lines[i].isBlank()) {
                return lines[i].stripTrailing().endsWith(suffix);
            }
        }
        return false;
    }

    private static String scoreboard(String output) {
        List<String> rows = new ArrayList<>();
        for (String line : output.split("\\R")) {
            if (SCOREBOARD_LINE.matcher(line).matches()) {
                rows.add(line.trim());
            }
        }
        return String.join("\n", rows);
    }

    private static String abbreviate(String text) {
        String flat = text.replace('\n', ' ').replace('\r', ' ').trim();
        return flat.length() <= 80 ? flat : flat.substring(0, 80) + "…";
    }

    private static DatapackRunResult result(DatapackCase testCase, List<DatapackStep> steps,
                                            boolean enabled, String functionOutput, String scoreboard,
                                            boolean passed, String failedStep, List<String> notes) {
        return new DatapackRunResult(testCase.id(), steps, enabled, functionOutput, scoreboard, passed,
                failedStep, notes.isEmpty() ? null : String.join("；", notes));
    }
}
