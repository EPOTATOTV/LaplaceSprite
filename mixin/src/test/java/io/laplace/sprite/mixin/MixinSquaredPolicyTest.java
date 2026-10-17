package io.laplace.sprite.mixin;

import io.laplace.sprite.core.model.PlatformFamily;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MixinSquared 调解（15.11 P2）：只对 Forge/NeoForge 启用。 */
class MixinSquaredPolicyTest {

    @Test
    void enabledForForgeAndNeoForge() {
        assertTrue(MixinSquaredPolicy.forFamily(PlatformFamily.NEOFORGE).enabled());
        assertTrue(MixinSquaredPolicy.forFamily(PlatformFamily.FORGE).enabled());
        assertTrue(MixinSquaredPolicy.forFamily(PlatformFamily.NEOFORGE).distributionNote().contains("JarInJar"));
    }

    @Test
    void disabledForFabricWithNativeOrderingReason() {
        MixinSquaredPolicy policy = MixinSquaredPolicy.forFamily(PlatformFamily.FABRIC);

        assertFalse(policy.enabled());
        assertTrue(policy.reason().contains("Mixin 0.8.x"), policy.reason());
        assertTrue(policy.reason().contains("排序"), policy.reason());
        assertFalse(MixinSquaredPolicy.forFamily(PlatformFamily.QUILT).enabled());
    }

    @Test
    void otherFamiliesAreNotApplicable() {
        assertFalse(MixinSquaredPolicy.forFamily(PlatformFamily.PAPER).enabled());
    }
}
