package io.laplace.sprite.report;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 一条全局审计事件（v3.0 L3-04）。
 *
 * @param type       事件类型
 * @param subject    事件主体，如版本 id、实例 id
 * @param detail     可读细节，如下载 URL / 哈希
 * @param at         发生时间
 * @param attributes 结构化附加字段
 */
public record AuditEvent(AuditType type, String subject, String detail, Instant at,
                         Map<String, String> attributes) {

    public AuditEvent {
        Objects.requireNonNull(type, "审计事件类型不能为空");
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("审计事件主体不能为空");
        }
        detail = detail == null ? "" : detail;
        at = at == null ? Instant.now() : at;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
