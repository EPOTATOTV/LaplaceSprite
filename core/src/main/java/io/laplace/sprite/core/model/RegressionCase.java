package io.laplace.sprite.core.model;

import java.time.Instant;

/**
 * 回归用例（4.6 RegressionLibrary）：误报修复必须携带回归轨迹入库，进入 CI 门禁。
 *
 * @param id            用例 id
 * @param flagId        来源 flag id
 * @param trajectoryRef 回归轨迹引用
 * @param fixRef        修复提交 / 变更引用
 * @param note          说明
 * @param addedAt       入库时间
 */
public record RegressionCase(String id, String flagId, String trajectoryRef, String fixRef,
                             String note, Instant addedAt) {
}
