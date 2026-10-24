package io.laplace.sprite.core.trust;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 资产可信度（L3-04）：未知 / 低分 / 吊销拒绝，重复注册以最新记录覆盖。 */
class AssetTrustRegistryTest {

    private static final Instant AT = Instant.parse("2026-01-01T00:00:00Z");

    private final AssetTrustRegistry registry = new AssetTrustRegistry();

    @Test
    void unknownAssetIsDeniedByDefault() {
        TrustDecision decision = registry.evaluate("mod-x", 0.5);
        assertFalse(decision.allowed());
        assertTrue(decision.reason().contains("未知资产"));
    }

    @Test
    void lowScoreIsDenied() {
        registry.register(new TrustRecord("mod-a", "publisher", 0.3, false, "评分低", AT));
        assertFalse(registry.evaluate("mod-a", 0.8).allowed());
    }

    @Test
    void revokedAssetIsDeniedEvenWithHighScore() {
        registry.register(new TrustRecord("mod-a", "publisher", 0.95, false, "", AT));
        registry.revoke("mod-a", "供应链投毒");

        TrustDecision decision = registry.evaluate("mod-a", 0.5);
        assertFalse(decision.allowed());
        assertTrue(decision.reason().contains("已吊销"));
        assertTrue(decision.reason().contains("供应链投毒"));
    }

    @Test
    void highScoreAboveThresholdPasses() {
        registry.register(new TrustRecord("mod-a", "publisher", 0.9, false, "", AT));
        TrustDecision decision = registry.evaluate("mod-a", 0.8);

        assertTrue(decision.allowed());
        assertTrue(registry.record("mod-a").isPresent());
    }

    @Test
    void duplicateRegistrationOverwritesPreviousRecord() {
        registry.register(new TrustRecord("mod-a", "old", 0.3, false, "", AT));
        registry.register(new TrustRecord("mod-a", "new", 0.95, false, "", AT.plusSeconds(1)));

        TrustRecord latest = registry.record("mod-a").orElseThrow();
        assertEquals("new", latest.publisher());
        assertEquals(0.95, latest.score());
        assertTrue(registry.evaluate("mod-a", 0.9).allowed());
    }
}
