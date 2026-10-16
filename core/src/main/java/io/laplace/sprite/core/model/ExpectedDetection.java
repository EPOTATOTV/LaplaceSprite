package io.laplace.sprite.core.model;

/**
 * 单个配置档位的预期检出（第五章 CheatTier）。
 *
 * @param expectedDetected 预期是否被检出
 * @param minConfidence    最低置信度（0-1）；检出率统计的下限
 * @param boundary         是否为阈值边界样本（「刚超阈值触发 / 阈值内不触发」）
 * @param note             说明
 */
public record ExpectedDetection(boolean expectedDetected, double minConfidence, boolean boundary,
                                String note) {

    public static ExpectedDetection detected() {
        return new ExpectedDetection(true, 0.9, false, null);
    }

    public static ExpectedDetection notDetected() {
        return new ExpectedDetection(false, 0.0, false, "阈值内不应触发");
    }

    /** 阈值边界样本：刚超阈值，允许贴线检出。 */
    public static ExpectedDetection boundarySample() {
        return new ExpectedDetection(true, 0.5, true, "阈值边界样本");
    }
}
