package io.laplace.sprite.hotreload;

import java.util.List;
import java.util.Locale;

/**
 * 热插拔禁忌检测（15.2）：被测插件用 reload API 热插拔时，该轮报告挂「⚠ 非真实运行态」，
 * 结果不进入发布门禁——reload 后的内存泄漏、监听器重复注册、调度器损坏会把缺陷盖住。
 */
public final class ReloadMisuseDetector {

    /** 文档指定的禁忌标记，字面量不要改。 */
    public static final String MARKER = "⚠ 非真实运行态";

    private static final List<String> SIGNATURES = List.of(
            "pluginmanager.reload",
            "reloadplugins",
            "reloadplugin(",
            "javapluginloader",
            "/reload confirm",
            "server.reload()");

    /**
     * 检测结论。
     *
     * @param misused 是否命中热插拔
     * @param evidence 命中的日志行
     */
    public record MisuseFinding(boolean misused, List<String> evidence) {

        public MisuseFinding {
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }
    }

    public MisuseFinding inspect(List<String> logLines) {
        if (logLines == null || logLines.isEmpty()) {
            return new MisuseFinding(false, List.of());
        }
        List<String> evidence = logLines.stream()
                .filter(line -> line != null && containsSignature(line))
                .toList();
        return new MisuseFinding(!evidence.isEmpty(), evidence);
    }

    public boolean misused(List<String> logLines) {
        return inspect(logLines).misused();
    }

    /** 命中即给报告挂标记并挡在发布门禁之外；未命中原样返回。 */
    public HotReloadReport apply(HotReloadReport report, List<String> logLines) {
        return misused(logLines) ? report.withMisuse(MARKER) : report;
    }

    private static boolean containsSignature(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return SIGNATURES.stream().anyMatch(lower::contains);
    }
}
