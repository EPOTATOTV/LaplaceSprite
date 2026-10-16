package io.laplace.sprite.adapters;

import io.laplace.sprite.adapters.sim.HeuristicDetectionEngine;
import io.laplace.sprite.cheats.threshold.DetectionThresholdAnchor;
import io.laplace.sprite.core.model.CheatType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 作弊阈值锚点与检测引擎的一致性守卫（v1.1.0 §4.2）。
 *
 * <p>{@link DetectionThresholdAnchor} 里的阈值与 check 名是照着 {@link HeuristicDetectionEngine} 抄的，
 * 两边一旦漂移，样本注入幅度就会算偏且没人发现。这里用反射把私有阈值常量读出来逐项对账。</p>
 */
class DetectionThresholdAnchorCrossCheckTest {

    /** 作弊类型 → 引擎 check 常量名。 */
    private static final Map<CheatType, String> CHECK_FIELDS = Map.ofEntries(
            Map.entry(CheatType.SPEED, "CHECK_SPEED"),
            Map.entry(CheatType.FLY, "CHECK_FLY"),
            Map.entry(CheatType.REACH, "CHECK_REACH"),
            Map.entry(CheatType.KILL_AURA, "CHECK_KILL_AURA"),
            Map.entry(CheatType.AUTO_CLICKER, "CHECK_AUTO_CLICKER"),
            Map.entry(CheatType.TIMER, "CHECK_TIMER"),
            Map.entry(CheatType.BLINK, "CHECK_BLINK"),
            Map.entry(CheatType.NO_FALL, "CHECK_NO_FALL"),
            Map.entry(CheatType.JESUS, "CHECK_JESUS"),
            Map.entry(CheatType.PHASE, "CHECK_PHASE"),
            Map.entry(CheatType.SCAFFOLD, "CHECK_SCAFFOLD"),
            Map.entry(CheatType.FAST_PLACE, "CHECK_FAST_PLACE"),
            Map.entry(CheatType.FAST_EAT, "CHECK_FAST_EAT"),
            Map.entry(CheatType.AUTO_TOTEM, "CHECK_AUTO_TOTEM"),
            Map.entry(CheatType.XRAY, "CHECK_XRAY"));

    /** 作弊类型 → 引擎私有阈值常量名；FLY 的阈值由两个常量相除得到。 */
    private static final Map<CheatType, String> VALUE_FIELDS = Map.ofEntries(
            Map.entry(CheatType.SPEED, "GROUND_SPEED_LIMIT"),
            Map.entry(CheatType.FLY, "FLY_MIN_CLIMB"),
            Map.entry(CheatType.REACH, "REACH_LIMIT"),
            Map.entry(CheatType.KILL_AURA, "KILL_AURA_MIN_TARGETS"),
            Map.entry(CheatType.AUTO_CLICKER, "AUTO_CLICK_MIN_CPS"),
            Map.entry(CheatType.TIMER, "TIMER_MIN_DUPLICATED_TICKS"),
            Map.entry(CheatType.BLINK, "BLINK_DISTANCE"),
            Map.entry(CheatType.NO_FALL, "NO_FALL_FALL_DELTA"),
            Map.entry(CheatType.PHASE, "PHASE_MIN_TICKS"),
            Map.entry(CheatType.JESUS, "JESUS_MIN_TICKS"),
            Map.entry(CheatType.SCAFFOLD, "SCAFFOLD_RATIO"),
            Map.entry(CheatType.FAST_PLACE, "FAST_PLACE_MIN_PER_TICK"),
            Map.entry(CheatType.FAST_EAT, "FAST_EAT_MIN_TICKS"),
            Map.entry(CheatType.AUTO_TOTEM, "AUTO_TOTEM_MAX_DELAY"),
            Map.entry(CheatType.XRAY, "XRAY_MAX_MINE_REACH"));

    @Test
    void anchorsCoverEveryCheatType() {
        assertEquals(Set.of(CheatType.values()), DetectionThresholdAnchor.covered());
        assertEquals(CHECK_FIELDS.keySet(), DetectionThresholdAnchor.covered());
        assertEquals(VALUE_FIELDS.keySet(), DetectionThresholdAnchor.covered());
    }

    @Test
    void anchorCheckNamesEqualEngineConstants() {
        for (CheatType type : CheatType.values()) {
            DetectionThresholdAnchor anchor = DetectionThresholdAnchor.of(type);
            assertEquals(stringConstant(CHECK_FIELDS.get(type)), anchor.checkName(),
                    type + " 的 check 名与引擎常量不一致");
        }
    }

    @Test
    void anchorValuesEqualEngineConstants() {
        for (CheatType type : CheatType.values()) {
            DetectionThresholdAnchor anchor = DetectionThresholdAnchor.of(type);
            assertEquals(expectedValue(type), anchor.value(), 1e-9,
                    type + " 的阈值与引擎常量不一致");
        }
    }

    @Test
    void everyDetectableAnchorHasAnEngineCheck() {
        long engineChecks = Arrays.stream(HeuristicDetectionEngine.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .filter(field -> field.getName().startsWith("CHECK_"))
                .count();
        long detectable = Arrays.stream(CheatType.values())
                .map(DetectionThresholdAnchor::of)
                .filter(anchor -> !anchor.undetectable())
                .count();
        assertEquals(detectable, engineChecks, "引擎 check 数与可检出锚点数不一致");

        for (CheatType type : CheatType.values()) {
            DetectionThresholdAnchor anchor = DetectionThresholdAnchor.of(type);
            if (anchor.undetectable()) {
                assertFalse(hasField(CHECK_FIELDS.get(type)), type + " 标为不可检出但引擎仍有 check");
            } else {
                assertTrue(hasField(CHECK_FIELDS.get(type)), type + " 缺少引擎 check");
            }
        }
    }

    /** FLY 的锚点是「累计爬升 / 连续 tick」；其余直接对齐单一常量。 */
    private static double expectedValue(CheatType type) {
        if (type == CheatType.FLY) {
            return numberConstant("FLY_MIN_CLIMB") / numberConstant("FLY_MIN_TICKS");
        }
        return numberConstant(VALUE_FIELDS.get(type));
    }

    private static Object constant(String name) {
        try {
            Field field = HeuristicDetectionEngine.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("引擎常量缺失: " + name, ex);
        }
    }

    private static double numberConstant(String name) {
        return ((Number) constant(name)).doubleValue();
    }

    private static String stringConstant(String name) {
        return (String) constant(name);
    }

    private static boolean hasField(String name) {
        List<String> names = Arrays.stream(HeuristicDetectionEngine.class.getDeclaredFields())
                .map(Field::getName)
                .toList();
        return names.contains(name);
    }
}
