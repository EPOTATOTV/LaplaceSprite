package io.laplace.sprite.cheats.threshold;

import io.laplace.sprite.core.model.CheatTierName;
import io.laplace.sprite.core.model.CheatType;

import java.util.EnumMap;
import java.util.Map;

/**
 * 档位系数（v1.1.0 §4.2）：min / default / max / hidden 四档施加在阈值锚点上的倍率。
 *
 * <p>注入幅度 = {@link DetectionThresholdAnchor#of} 的阈值 × 本类给出的档位系数。统一档位系数
 * {@code MIN=1.1 / DEFAULT=1.5 / MAX=2.0 / HIDDEN=1.2} 适用于「主参数就是引擎阈值同一量纲、且越大越可疑」
 * 的类型；当注入器的参数刻度与引擎阈值刻度不一致（倍率、计数、高度等），统一系数在物理上不成立，
 * 按类型覆盖并在此逐条说明。</p>
 */
public final class TierCoefficient {

    /** 统一档位系数：MIN 贴阈值边界，DEFAULT/MAX 明显超过阈值，HIDDEN 接近阈值但仍越界。 */
    private static final Map<CheatTierName, Double> UNIFORM = uniform();

    /** 需要按类型覆盖的档位系数。 */
    private static final Map<CheatType, Map<CheatTierName, Double>> OVERRIDES = overrides();

    private TierCoefficient() {
    }

    /** 统一档位系数；未覆盖的类型使用该值。 */
    public static double of(CheatTierName tier) {
        Double coefficient = UNIFORM.get(tier);
        if (coefficient == null) {
            throw new IllegalArgumentException("未定义档位系数: " + tier);
        }
        return coefficient;
    }

    /** 指定类型的档位系数，优先取覆盖值。 */
    public static double of(CheatType type, CheatTierName tier) {
        Map<CheatTierName, Double> override = OVERRIDES.get(type);
        return override == null ? of(tier) : override.get(tier);
    }

    /** 注入幅度 = 阈值锚点 × 档位系数。 */
    public static double magnitude(CheatType type, CheatTierName tier) {
        return DetectionThresholdAnchor.of(type).value() * of(type, tier);
    }

    private static Map<CheatTierName, Double> uniform() {
        Map<CheatTierName, Double> map = new EnumMap<>(CheatTierName.class);
        map.put(CheatTierName.MIN, 1.1);
        map.put(CheatTierName.DEFAULT, 1.5);
        map.put(CheatTierName.MAX, 2.0);
        map.put(CheatTierName.HIDDEN, 1.2);
        return map;
    }

    private static Map<CheatType, Map<CheatTierName, Double>> overrides() {
        Map<CheatType, Map<CheatTierName, Double>> overrides = new EnumMap<>(CheatType.class);
        // SPEED：锚点是 0.30 格/tick 的位移阈值，注入器 speed 是对基线位移的倍率，两者不同量纲；
        // 参考疾跑基线约 0.28 格/tick，越界倍率约 1.07，故系数需放大到 3.7 起才能让 0.30×系数>1.07。
        overrides.put(CheatType.SPEED, tierMap(3.7, 4.5, 6.3, 4.0));
        // TIMER：锚点是重复位置包计数下限（3 次），注入器 speedup 是计时倍率，系数把计数刻度换算成倍率刻度。
        overrides.put(CheatType.TIMER, tierMap(0.3667, 0.5, 0.6667, 0.4167));
        // JESUS：锚点是流体表面落地计数下限（2 次），注入器 surfaceY 是水面高度（格），
        // 系数把计数刻度换算成高度刻度，使注入液面落在游泳基线的液面附近。
        overrides.put(CheatType.JESUS, tierMap(31.2, 31.5, 32.0, 31.3));
        // SCAFFOLD：锚点是脚下补块移动占比下限（0.8），注入器 blocksPerTick 是每 tick 补块数，
        // 系数把占比刻度换算成计数刻度（映射到整数补块数）。
        overrides.put(CheatType.SCAFFOLD, tierMap(1.25, 2.5, 3.75, 1.25));
        // FAST_EAT：引擎判「耗时 < 16 tick」，越小越可疑，系数需小于 1 才能落到越界一侧。
        overrides.put(CheatType.FAST_EAT, tierMap(0.875, 0.25, 0.125, 0.75));
        // AUTO_TOTEM：引擎判「换手延迟 ≤ 4 tick」，MIN 取边界值本身，其余档更小更可疑。
        overrides.put(CheatType.AUTO_TOTEM, tierMap(1.0, 0.5, 0.25, 0.75));
        return overrides;
    }

    private static Map<CheatTierName, Double> tierMap(double min, double defaultTier, double max, double hidden) {
        Map<CheatTierName, Double> map = new EnumMap<>(CheatTierName.class);
        map.put(CheatTierName.MIN, min);
        map.put(CheatTierName.DEFAULT, defaultTier);
        map.put(CheatTierName.MAX, max);
        map.put(CheatTierName.HIDDEN, hidden);
        return map;
    }
}
