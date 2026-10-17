package io.laplace.sprite.mixin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Forge 旧包兼容指引（15.11 P2）。 */
class OldMixinCompatibilityTest {

    @Test
    void neededForOneTenToSixteenRange() {
        assertTrue(OldMixinCompatibility.needed("1.10.2"));
        assertTrue(OldMixinCompatibility.needed("1.12.2"));
        assertTrue(OldMixinCompatibility.needed("1.16.5"));
    }

    @Test
    void notNeededOutsideRange() {
        assertFalse(OldMixinCompatibility.needed("1.9.4"));
        assertFalse(OldMixinCompatibility.needed("1.17"));
        assertFalse(OldMixinCompatibility.needed("1.21.1"));
        assertFalse(OldMixinCompatibility.needed(""));
        assertFalse(OldMixinCompatibility.needed(null));
    }

    @Test
    void guidanceIsExposedAsData() {
        assertTrue(OldMixinCompatibility.catalog().get(0).artifact().contains("NotStirred"));
        assertTrue(OldMixinCompatibility.NOT_STIRRED.guidance().contains("0.8"));
    }
}
