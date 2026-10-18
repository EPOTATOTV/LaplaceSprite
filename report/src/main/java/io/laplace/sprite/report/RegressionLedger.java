package io.laplace.sprite.report;

import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.core.model.FlagEvent;
import io.laplace.sprite.core.model.RegressionCase;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 回归台账（升级实施文档 L-03）：把回归用例、误报 flag 与绕过报告放在同一目录持久化。
 *
 * <p>运行结束时由 {@code laplace run} 写入，门禁阶段由 {@code laplace gate} 读取；
 * 健康度同时落 {@code health.json} 与 {@code health.md}，便于报告直接引用。</p>
 */
public final class RegressionLedger {

    public static final String LIBRARY_FILE = "library.json";
    public static final String FLAGS_FILE = "flags.json";
    public static final String BYPASS_FILE = "bypass.json";
    public static final String HEALTH_FILE = "health.json";
    public static final String HEALTH_MARKDOWN_FILE = "health.md";

    private final Path root;
    private final Map<String, RegressionCase> cases = new LinkedHashMap<>();
    private final Map<String, FlagEvent> flags = new LinkedHashMap<>();
    private final Map<String, RegressionHealth.BypassReport> bypasses = new LinkedHashMap<>();

    public RegressionLedger(Path root) {
        this.root = root;
        loadCases();
        loadFlags();
        loadBypasses();
    }

    public Path root() {
        return root;
    }

    public Path libraryFile() {
        return root.resolve(LIBRARY_FILE);
    }

    public Path flagsFile() {
        return root.resolve(FLAGS_FILE);
    }

    public Path bypassFile() {
        return root.resolve(BYPASS_FILE);
    }

    public Path healthFile() {
        return root.resolve(HEALTH_FILE);
    }

    public synchronized void recordCases(List<RegressionCase> entries) {
        if (entries == null) {
            return;
        }
        entries.forEach(entry -> cases.putIfAbsent(entry.id(), entry));
        persist(libraryFile(), List.copyOf(cases.values()));
    }

    public synchronized void recordFlags(List<FlagEvent> entries) {
        if (entries == null) {
            return;
        }
        entries.forEach(entry -> flags.put(entry.id(), entry));
        persist(flagsFile(), List.copyOf(flags.values()));
    }

    public synchronized void recordBypasses(List<RegressionHealth.BypassReport> entries) {
        if (entries == null) {
            return;
        }
        entries.forEach(entry -> bypasses.putIfAbsent(entry.id(), entry));
        persist(bypassFile(), List.copyOf(bypasses.values()));
    }

    /** 把一条绕过报告标记为已转化；不存在时返回 false。 */
    public synchronized boolean convertBypass(String id) {
        RegressionHealth.BypassReport existing = bypasses.get(id);
        if (existing == null) {
            return false;
        }
        bypasses.put(id, new RegressionHealth.BypassReport(existing.id(), existing.cheatType(),
                existing.reportedAt(), true));
        persist(bypassFile(), List.copyOf(bypasses.values()));
        return true;
    }

    public List<RegressionCase> cases() {
        return List.copyOf(cases.values());
    }

    public List<FlagEvent> flags() {
        return List.copyOf(flags.values());
    }

    public List<RegressionHealth.BypassReport> bypasses() {
        return List.copyOf(bypasses.values());
    }

    public RegressionHealth.Health health() {
        return RegressionHealth.evaluate(cases(), flags(), bypasses(),
                RegressionHealth.Thresholds.defaults(), Instant.now());
    }

    /** 写入健康度快照与 Markdown 摘要。 */
    public RegressionHealth.Health writeHealth() {
        RegressionHealth.Health health = health();
        persist(healthFile(), health);
        writeText(root.resolve(HEALTH_MARKDOWN_FILE), toMarkdown(health));
        return health;
    }

    public static String toMarkdown(RegressionHealth.Health health) {
        StringBuilder md = new StringBuilder();
        md.append("# 回归库健康度\n\n");
        md.append("| 指标 | 值 |\n| --- | ---: |\n");
        md.append("| 回归用例 | ").append(health.caseCount()).append(" |\n");
        md.append("| 绕过报告 | ").append(health.bypassReports()).append(" |\n");
        md.append("| 已转化 | ").append(health.convertedBypasses()).append(" |\n");
        md.append("| 转化率 | ").append(percent(health.bypassConversionRate())).append(" |\n");
        md.append("| 误报 flag | ").append(health.falsePositiveFlags()).append(" |\n");
        md.append("| 已复现误报 | ").append(health.coveredFalsePositives()).append(" |\n");
        md.append("| 复现覆盖率 | ").append(percent(health.falsePositiveCoverage())).append(" |\n");
        md.append("| 超期未转化 | ").append(health.overdueBypasses()).append(" |\n");
        md.append("\n结论：").append(health.healthy() ? "通过" : "未通过").append('\n');
        if (!health.violations().isEmpty()) {
            health.violations().forEach(line -> md.append("- ").append(line).append('\n'));
        }
        return md.toString();
    }

    private static String percent(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f%%", value * 100);
    }

    private void loadCases() {
        for (RegressionCase entry : readArray(libraryFile(), RegressionCase[].class)) {
            cases.put(entry.id(), entry);
        }
    }

    private void loadFlags() {
        for (FlagEvent entry : readArray(flagsFile(), FlagEvent[].class)) {
            flags.put(entry.id(), entry);
        }
    }

    private void loadBypasses() {
        for (RegressionHealth.BypassReport entry : readArray(bypassFile(),
                RegressionHealth.BypassReport[].class)) {
            bypasses.put(entry.id(), entry);
        }
    }

    private static <T> List<T> readArray(Path file, Class<T[]> type) {
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (content.isBlank()) {
                return List.of();
            }
            return new ArrayList<>(Arrays.asList(Json.jsonMapper().readValue(content, type)));
        } catch (IOException ex) {
            throw new UncheckedIOException("读取回归台账失败: " + file, ex);
        }
    }

    private static void persist(Path file, Object value) {
        Json.writeFile(file, value);
    }

    private static void writeText(Path file, String content) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("写入健康度摘要失败: " + file, ex);
        }
    }
}
