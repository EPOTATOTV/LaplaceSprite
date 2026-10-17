package io.laplace.sprite.datapack;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code /test} 命令回包的解析结果（15.8）。
 *
 * <p>支持的形态是原版 1.21.5 起的常见回显：{@code Test structure created at ...}、
 * {@code Nearest test structure at ...}、{@code Cleared all test structures}、
 * {@code Test passed!} / {@code Test failed: ...} 以及 {@code N tests passed} 这类计数行。</p>
 *
 * @param created 是否回显了测试结构创建
 * @param located 是否回显了测试结构定位
 * @param cleared 是否回显了测试结构清理
 * @param passed  是否判通过
 * @param failed  是否判失败
 * @param output  原始回显
 */
public record GameTestResponse(boolean created, boolean located, boolean cleared, boolean passed,
                               boolean failed, String output) {

    private static final Pattern PASSED_COUNT = Pattern.compile("(\\d+)\\s+tests?\\s+passed");
    private static final Pattern FAILED_COUNT = Pattern.compile("(\\d+)\\s+tests?\\s+failed");

    public GameTestResponse {
        output = output == null ? "" : output;
    }

    public static GameTestResponse parse(String reply) {
        String output = reply == null ? "" : reply;
        String lower = output.toLowerCase(Locale.ROOT);
        boolean created = lower.contains("test structure created")
                || lower.contains("created a test structure")
                || lower.contains("created test structure");
        boolean located = lower.contains("nearest test structure")
                || lower.contains("test structure located")
                || lower.contains("located test");
        boolean cleared = lower.contains("cleared all test structures")
                || (lower.contains("cleared") && lower.contains("test structure"));
        int passedCount = sumMatches(PASSED_COUNT, lower);
        int failedCount = sumMatches(FAILED_COUNT, lower);
        boolean failed = failedCount > 0
                || lower.contains("test failed")
                || lower.contains("failed!");
        boolean passed = !failed && (passedCount > 0
                || lower.contains("test passed")
                || lower.contains("passed!"));
        return new GameTestResponse(created, located, cleared, passed, failed, output);
    }

    private static int sumMatches(Pattern pattern, String text) {
        int total = 0;
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            total += Integer.parseInt(matcher.group(1));
        }
        return total;
    }
}
