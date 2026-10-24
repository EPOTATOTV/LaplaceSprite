package io.laplace.sprite.core.trust;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 资产可信度登记（v3.0 L3-04）：默认不信任——未知资产、已吊销资产、分数低于阈值的资产都拒绝。
 *
 * <p>登记是纯内存快照，持久化由调用方决定；重复注册同一资产直接覆盖，吊销后再次注册也会被覆盖，
 * 因此吊销要走 {@link #revoke} 并保留记录。</p>
 */
public final class AssetTrustRegistry {

    private static final String UNKNOWN_PUBLISHER = "unknown";

    private final Map<String, TrustRecord> records = new LinkedHashMap<>();

    public TrustRecord register(TrustRecord record) {
        records.put(record.assetId(), record);
        return record;
    }

    /** 吊销已登记资产；未登记过也写入一条保守记录，避免后续同名资产被误放行。 */
    public TrustRecord revoke(String assetId, String reason) {
        TrustRecord existing = records.get(assetId);
        TrustRecord revoked = new TrustRecord(assetId,
                existing == null ? UNKNOWN_PUBLISHER : existing.publisher(),
                existing == null ? 0.0 : existing.score(),
                true,
                reason,
                Instant.now());
        records.put(assetId, revoked);
        return revoked;
    }

    public Optional<TrustRecord> record(String assetId) {
        return Optional.ofNullable(records.get(assetId));
    }

    public TrustDecision evaluate(String assetId, double minScore) {
        TrustRecord record = assetId == null ? null : records.get(assetId);
        if (record == null) {
            return new TrustDecision(false, "未知资产，默认不信任");
        }
        if (record.revoked()) {
            return new TrustDecision(false, "资产已吊销" + (record.reason().isBlank()
                    ? "" : "：" + record.reason()));
        }
        if (record.score() < minScore) {
            return new TrustDecision(false, "可信分 %.2f 低于阈值 %.2f"
                    .formatted(record.score(), minScore));
        }
        return new TrustDecision(true, "可信分 %.2f 达到阈值 %.2f"
                .formatted(record.score(), minScore));
    }
}
