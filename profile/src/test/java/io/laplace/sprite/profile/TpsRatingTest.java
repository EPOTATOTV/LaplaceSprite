package io.laplace.sprite.profile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TPS 评级档位（15.3）：边界必须钉死。 */
class TpsRatingTest {

    @Test
    void bandBoundaries() {
        assertEquals(TpsRating.PERFECT, TpsRating.of(20.0));
        assertEquals(TpsRating.PERFECT, TpsRating.of(21.5));
        assertEquals(TpsRating.SMOOTH, TpsRating.of(19.99));
        assertEquals(TpsRating.SMOOTH, TpsRating.of(19.5));
        assertEquals(TpsRating.SLIGHT_LAG, TpsRating.of(19.49));
        assertEquals(TpsRating.SLIGHT_LAG, TpsRating.of(18.0));
        assertEquals(TpsRating.NOTICEABLE_LAG, TpsRating.of(17.99));
        assertEquals(TpsRating.NOTICEABLE_LAG, TpsRating.of(15.0));
        assertEquals(TpsRating.SEVERE_LAG, TpsRating.of(14.99));
        assertEquals(TpsRating.SEVERE_LAG, TpsRating.of(10.0));
        assertEquals(TpsRating.UNUSABLE, TpsRating.of(9.99));
        assertEquals(TpsRating.UNUSABLE, TpsRating.of(0.0));
        assertEquals(TpsRating.UNUSABLE, TpsRating.of(-3.0));
    }

    @Test
    void ratingIsMonotonicDownTheLadder() {
        TpsRating[] ladder = {TpsRating.PERFECT, TpsRating.SMOOTH, TpsRating.SLIGHT_LAG,
                TpsRating.NOTICEABLE_LAG, TpsRating.SEVERE_LAG, TpsRating.UNUSABLE};
        for (int i = 1; i < ladder.length; i++) {
            assertTrue(ladder[i - 1].minTps() > ladder[i].minTps(),
                    ladder[i - 1] + " 的下界必须高于 " + ladder[i]);
        }
    }

    @Test
    void labelsAreHumanReadableChinese() {
        assertEquals("完美无卡顿", TpsRating.PERFECT.label());
        assertEquals("流畅", TpsRating.SMOOTH.label());
        assertEquals("略有卡顿", TpsRating.SLIGHT_LAG.label());
        assertEquals("明显卡顿", TpsRating.NOTICEABLE_LAG.label());
        assertEquals("严重卡顿", TpsRating.SEVERE_LAG.label());
        assertEquals("不可用", TpsRating.UNUSABLE.label());
        assertEquals("略有卡顿（19.4 TPS）", TpsRating.describe(19.4));
    }

    @Test
    void smoothFloorMatchesRegressionThreshold() {
        assertEquals(ProfileCollector.TPS_REGRESSION_THRESHOLD, TpsRating.SMOOTH.minTps(), 1.0e-9);
    }
}
