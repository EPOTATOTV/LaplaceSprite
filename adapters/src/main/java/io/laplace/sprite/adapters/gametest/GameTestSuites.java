package io.laplace.sprite.adapters.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 原生 GameTest 控制台输出解析（14.6）。
 *
 * <p>输入是 {@code /test runall} 的日志行与进程退出码。失败数从日志文本推导，再与退出码核对：
 * 两者不一致说明日志被截断或退出码被包装，宁可报错也不猜。</p>
 */
public final class GameTestSuites {

    /** 汇总行：{@code 12 tests passed, 2 tests failed}。 */
    private static final Pattern SUMMARY = Pattern.compile(
            "^(\\d+)\\s+tests?\\s+passed,\\s*(\\d+)\\s+tests?\\s+failed\\.?$");

    /** 单条用例行：{@code test.example.Foo passed!} / {@code test.example.Bar failed! 期望命中}。 */
    private static final Pattern CASE = Pattern.compile("^([\\w.$]+)\\s+(passed|failed)\\s*!?\\s*(.*)$");

    private GameTestSuites() {
    }

    public static NativeGameTestResult fromNative(int exitCode, List<String> lines) {
        List<GameTestCaseResult> cases = new ArrayList<>();
        Integer reportedTotal = null;
        Integer reportedFailed = null;

        for (String raw : lines) {
            String line = stripLogPrefix(raw);
            Matcher summary = SUMMARY.matcher(line);
            if (summary.matches()) {
                int passed = Integer.parseInt(summary.group(1));
                int failed = Integer.parseInt(summary.group(2));
                reportedTotal = passed + failed;
                reportedFailed = failed;
                continue;
            }
            Matcher test = CASE.matcher(line);
            if (test.matches()) {
                cases.add(new GameTestCaseResult(test.group(1), "passed".equals(test.group(2)),
                        cleanDetail(test.group(3))));
            }
        }

        int failed = reportedFailed != null ? reportedFailed
                : (int) cases.stream().filter(test -> !test.passed()).count();
        int total = reportedTotal != null ? reportedTotal : cases.size();
        if (failed != exitCode) {
            throw new IllegalArgumentException("退出码与日志不一致：退出码 %d，日志解析出的失败用例 %d 项"
                    .formatted(exitCode, failed));
        }
        return new NativeGameTestResult(total, failed, exitCode, cases);
    }

    /** 去掉 {@code [12:00:01] [Server thread/INFO]: } 前缀；没有前缀的行原样返回。 */
    private static String stripLogPrefix(String line) {
        String trimmed = line == null ? "" : line.trim();
        int marker = trimmed.indexOf("]: ");
        return marker >= 0 ? trimmed.substring(marker + 3).trim() : trimmed;
    }

    /** 断言详情可能以冒号开头，抹掉分隔符只留正文。 */
    private static String cleanDetail(String detail) {
        String cleaned = detail == null ? "" : detail.trim();
        while (cleaned.startsWith(":") || cleaned.startsWith("：")) {
            cleaned = cleaned.substring(1).trim();
        }
        return cleaned;
    }
}
