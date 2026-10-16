package io.laplace.sprite.cheats;

import io.laplace.sprite.cheats.threshold.DetectionThresholdAnchor;
import io.laplace.sprite.cheats.threshold.SampleLibraryValidator;
import io.laplace.sprite.cheats.threshold.TierCoefficient;
import io.laplace.sprite.core.model.CheatSpec;
import io.laplace.sprite.core.model.CheatTier;
import io.laplace.sprite.core.model.CheatTierName;
import io.laplace.sprite.core.model.CheatType;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阈值锚点模型（v1.1.0 §4.2）在整份样本库上的强约束。
 *
 * <p>核对：锚点覆盖全部 15 种类型且元数据完整、注入幅度 = 锚点 × 档位系数、样本库声明值与该派生一致、
 * 档位语义（MIN 为边界档、DEFAULT/MAX 应检出）成立，以及锚点必须落在检测阈值之外（可检出）。</p>
 */
class SampleLibraryValidatorTest {

    /**
     * 主参数与锚点不同量纲、系数含刻度换算的类型（倍率、水面高度、补块数等）。
     * 这批类型的注入幅度不直接与锚点比大小，故不做「贴阈值边界」的数值断言。
     */
    private static final Set<CheatType> SCALE_CONVERTED = EnumSet.of(
            CheatType.SPEED, CheatType.TIMER, CheatType.JESUS, CheatType.SCAFFOLD,
            CheatType.FAST_EAT, CheatType.AUTO_TOTEM);

    private final CheatCatalog catalog = new CheatCatalog();

    @Test
    void libraryMatchesAnchorModel() {
        List<String> violations = new SampleLibraryValidator().violations();
        assertTrue(violations.isEmpty(), () -> "样本库不符合阈值锚点模型:\n" + String.join("\n", violations));
    }

    @Test
    void anchorsCoverEveryCheatType() {
        assertEquals(EnumSet.allOf(CheatType.class), DetectionThresholdAnchor.covered());
        for (CheatType type : CheatType.values()) {
            DetectionThresholdAnchor anchor = DetectionThresholdAnchor.of(type);
            assertEquals(type, anchor.type());
            assertFalse(anchor.checkName().isBlank(), type.name());
            assertFalse(anchor.paramKey().isBlank(), type.name());
            assertFalse(anchor.unit().isBlank(), type.name());
            assertFalse(anchor.note().isBlank(), type.name());
        }
    }

    @Test
    void everyAnchorIsDetectable() {
        // 当前引擎对每种类型都有对应 check；若某类型没有，应在此显式列出并写入绕过清单，而非硬凑阈值。
        Set<CheatType> undetectable = EnumSet.noneOf(CheatType.class);
        for (CheatType type : DetectionThresholdAnchor.covered()) {
            if (DetectionThresholdAnchor.of(type).undetectable()) {
                undetectable.add(type);
            }
        }
        assertTrue(undetectable.isEmpty(), () -> "尚无对应 check 的类型: " + undetectable);
    }

    @Test
    void magnitudeIsAnchorTimesCoefficient() {
        for (CheatType type : DetectionThresholdAnchor.covered()) {
            double anchor = DetectionThresholdAnchor.of(type).value();
            for (CheatTierName tier : CheatTierName.values()) {
                double coefficient = TierCoefficient.of(type, tier);
                assertEquals(anchor * coefficient, TierCoefficient.magnitude(type, tier), 1e-9,
                        type + "/" + tier);
            }
        }
    }

    @Test
    void minTierSitsAtThresholdEdge() {
        for (CheatType type : DetectionThresholdAnchor.covered()) {
            if (SCALE_CONVERTED.contains(type)) {
                continue;
            }
            DetectionThresholdAnchor anchor = DetectionThresholdAnchor.of(type);
            double min = TierCoefficient.magnitude(type, CheatTierName.MIN);
            assertTrue(min > anchor.value(),
                    () -> type + " 的 MIN 幅度须超过阈值: " + min + " <= " + anchor.value());
            double defaultValue = TierCoefficient.magnitude(type, CheatTierName.DEFAULT);
            assertTrue(defaultValue > min,
                    () -> type + " 的 DEFAULT 幅度须高于 MIN: " + defaultValue + " <= " + min);
        }
    }

    @Test
    void everySpecDeclaresPrimaryParamAsDerivedMagnitude() {
        for (CheatSpec spec : catalog.loadDefaults()) {
            DetectionThresholdAnchor anchor = DetectionThresholdAnchor.of(spec.type());
            Set<CheatTierName> declared = EnumSet.noneOf(CheatTierName.class);
            for (CheatTier tier : spec.tiers()) {
                declared.add(tier.tier());
                Object raw = tier.params().get(anchor.paramKey());
                assertTrue(raw instanceof Number, spec.id() + " " + tier.tier() + " 主参数缺失");
                double actual = ((Number) raw).doubleValue();
                assertEquals(TierCoefficient.magnitude(spec.type(), tier.tier()), actual, 1e-3,
                        spec.id() + " " + tier.tier());
                boolean shouldBoundary = tier.tier() == CheatTierName.MIN;
                assertEquals(shouldBoundary, tier.expected().boundary(), spec.id() + " " + tier.tier());
                assertTrue(tier.expected().expectedDetected(), spec.id() + " " + tier.tier());
            }
            assertEquals(EnumSet.allOf(CheatTierName.class), declared, spec.id());
        }
    }
}
