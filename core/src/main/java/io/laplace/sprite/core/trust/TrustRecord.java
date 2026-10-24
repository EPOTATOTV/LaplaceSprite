package io.laplace.sprite.core.trust;

import java.time.Instant;

/**
 * 资产可信记录（v3.0 L3-04）。
 *
 * @param assetId   资产 id，如 mod 文件哈希或市场条目 id
 * @param publisher 发布者
 * @param score     可信分，0–1
 * @param revoked   是否已吊销
 * @param reason    评分或吊销原因
 * @param updatedAt 最近更新时间
 */
public record TrustRecord(String assetId, String publisher, double score, boolean revoked,
                          String reason, Instant updatedAt) {

    public TrustRecord {
        if (assetId == null || assetId.isBlank()) {
            throw new IllegalArgumentException("资产 id 不能为空");
        }
        if (!Double.isFinite(score) || score < 0 || score > 1) {
            throw new IllegalArgumentException("可信分必须在 0–1 之间：" + score);
        }
        publisher = publisher == null ? "" : publisher;
        reason = reason == null ? "" : reason;
        updatedAt = updatedAt == null ? Instant.now() : updatedAt;
    }
}
