package io.laplace.sprite.core.model;

import java.time.Instant;

/**
 * 证据记录（4.6 EvidenceVault）：本地 + 可上云存储，重启保留。
 *
 * @param id          证据 id
 * @param runId       所属运行
 * @param kind        类型（log / screenshot / replay / metrics / frame）
 * @param path        存储路径
 * @param description 说明
 * @param createdAt   归档时间
 */
public record EvidenceRecord(String id, String runId, String kind, String path, String description,
                             Instant createdAt) {
}
