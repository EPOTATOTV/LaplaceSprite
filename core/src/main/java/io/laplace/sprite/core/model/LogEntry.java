package io.laplace.sprite.core.model;

import java.time.Instant;
import java.util.Map;

/**
 * 结构化日志条目（11.3 日志流 / 11.6 输出日志），logfmt / JSON 可被 EvidenceVault 直接归档。
 */
public record LogEntry(Instant at, String level, String module, String message, Map<String, Object> fields) {

    public LogEntry {
        fields = fields == null ? Map.of() : Map.copyOf(fields);
    }

    public static LogEntry info(String module, String message) {
        return new LogEntry(Instant.now(), "INFO", module, message, Map.of());
    }

    public static LogEntry flag(String module, String message) {
        return new LogEntry(Instant.now(), "FLAG", module, message, Map.of());
    }

    /** logfmt 单行输出。 */
    public String toLogfmt() {
        StringBuilder sb = new StringBuilder()
                .append("ts=").append(at)
                .append(" level=").append(level)
                .append(" module=").append(module)
                .append(" msg=").append('"').append(message.replace("\"", "'")).append('"');
        fields.forEach((k, v) -> sb.append(' ').append(k).append('=').append(v));
        return sb.toString();
    }
}
