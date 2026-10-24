package io.laplace.sprite.core.trust;

/**
 * 资产可信判定结论（v3.0 L3-04）。
 *
 * @param allowed 是否放行
 * @param reason  结论说明；放行时说明分数，拒绝时说明原因
 */
public record TrustDecision(boolean allowed, String reason) {

    public TrustDecision {
        reason = reason == null ? "" : reason;
    }
}
