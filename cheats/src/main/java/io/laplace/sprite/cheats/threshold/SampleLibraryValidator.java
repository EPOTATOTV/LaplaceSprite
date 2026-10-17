package io.laplace.sprite.cheats.threshold;

import io.laplace.sprite.cheats.CheatCatalog;
import io.laplace.sprite.core.model.TestSpec;
import io.laplace.sprite.core.model.SampleTier;
import io.laplace.sprite.core.model.SampleTierName;
import io.laplace.sprite.core.model.CheatType;
import io.laplace.sprite.core.model.ExpectedDetection;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 样本库一致性校验（v1.1.0 §4.2）：逐一核对「注入幅度 = 阈值锚点 × 档位系数」的派生关系。
 *
 * <p>校验项：样本数量下限、id 唯一、target 指向 detection-engine、档位恰为四档、
 * 主参数与其类型的锚点×系数一致（小容差内）、expected 与档位语义一致、
 * 每个已登记锚点的类型都有样本覆盖。返回的违规项为空即合格。</p>
 */
public final class SampleLibraryValidator {

    /** 注入幅度与锚点×系数之间允许的绝对误差。 */
    private static final double TOLERANCE = 1e-3;
    /** 条款要求样本数不少于 30。 */
    private static final int MIN_SPECS = 30;

    private final CheatCatalog catalog;

    public SampleLibraryValidator() {
        this(new CheatCatalog());
    }

    public SampleLibraryValidator(CheatCatalog catalog) {
        this.catalog = catalog;
    }

    /** 违规项为空则通过，否则抛出异常并列出全部违规。 */
    public void validate() {
        List<String> violations = violations();
        if (!violations.isEmpty()) {
            throw new IllegalStateException("样本库不符合阈值锚点模型:\n" + String.join("\n", violations));
        }
    }

    public List<String> violations() {
        List<String> problems = new ArrayList<>();
        checkAnchors(problems);
        List<TestSpec> specs = catalog.loadDefaults();
        if (specs.size() < MIN_SPECS) {
            problems.add("样本数不足: " + specs.size() + " < " + MIN_SPECS);
        }
        Set<String> ids = new HashSet<>();
        Set<CheatType> covered = EnumSet.noneOf(CheatType.class);
        for (TestSpec spec : specs) {
            if (!ids.add(spec.id())) {
                problems.add("样本 id 重复: " + spec.id());
            }
            if (!"detection-engine".equals(spec.target())) {
                problems.add(spec.id() + " 的 target 不是 detection-engine");
            }
            covered.add(spec.type());
            checkTiers(spec, problems);
        }
        for (CheatType type : DetectionThresholdAnchor.covered()) {
            if (!covered.contains(type)) {
                problems.add("锚点类型未被样本覆盖: " + type);
            }
        }
        return List.copyOf(problems);
    }

    private static void checkAnchors(List<String> problems) {
        for (CheatType type : CheatType.values()) {
            DetectionThresholdAnchor anchor = DetectionThresholdAnchor.of(type);
            if (anchor.checkName() == null || anchor.checkName().isBlank()) {
                problems.add("锚点缺 checkName: " + type);
            }
            if (anchor.paramKey() == null || anchor.paramKey().isBlank()) {
                problems.add("锚点缺 paramKey: " + type);
            }
            if (anchor.unit() == null || anchor.unit().isBlank()) {
                problems.add("锚点缺 unit: " + type);
            }
            if (anchor.note() == null || anchor.note().isBlank()) {
                problems.add("锚点缺 note: " + type);
            }
            if (!anchor.undetectable() && anchor.value() <= 0) {
                problems.add("可检出锚点的阈值必须为正: " + type);
            }
        }
    }

    private static void checkTiers(TestSpec spec, List<String> problems) {
        Set<SampleTierName> names = EnumSet.noneOf(SampleTierName.class);
        for (SampleTier tier : spec.tiers()) {
            if (!names.add(tier.tier())) {
                problems.add(spec.id() + " 档位重复: " + tier.tier());
            }
        }
        if (names.size() != SampleTierName.values().length) {
            problems.add(spec.id() + " 档位集合不是四档: " + names);
            return;
        }
        DetectionThresholdAnchor anchor = DetectionThresholdAnchor.of(spec.type());
        for (SampleTier tier : spec.tiers()) {
            checkExpected(spec, tier, anchor, problems);
            checkMagnitude(spec, tier, anchor, problems);
        }
    }

    private static void checkExpected(TestSpec spec, SampleTier tier, DetectionThresholdAnchor anchor,
                                      List<String> problems) {
        ExpectedDetection expected = tier.expected();
        if (expected == null) {
            problems.add(spec.id() + " " + tier.tier() + " 缺 expected");
            return;
        }
        boolean detectable = !anchor.undetectable();
        if (expected.expectedDetected() != detectable) {
            problems.add(spec.id() + " " + tier.tier() + " 的 expectedDetected 与可检出性不符");
        }
        boolean shouldBoundary = detectable && tier.tier() == SampleTierName.MIN;
        if (expected.boundary() != shouldBoundary) {
            problems.add(spec.id() + " " + tier.tier() + " 的 boundary 与档位语义不符");
        }
    }

    private static void checkMagnitude(TestSpec spec, SampleTier tier, DetectionThresholdAnchor anchor,
                                       List<String> problems) {
        if (anchor.undetectable()) {
            return;
        }
        Object raw = tier.params().get(anchor.paramKey());
        if (!(raw instanceof Number number)) {
            problems.add(spec.id() + " " + tier.tier() + " 缺少主参数 " + anchor.paramKey());
            return;
        }
        double actual = number.doubleValue();
        double expected = TierCoefficient.magnitude(spec.type(), tier.tier());
        if (Math.abs(actual - expected) > TOLERANCE) {
            problems.add("%s %s 主参数 %s=%.4f 与 锚点 %.4f × 系数 %.4f = %.4f 不符".formatted(
                    spec.id(), tier.tier(), anchor.paramKey(), actual, anchor.value(),
                    TierCoefficient.of(spec.type(), tier.tier()), expected));
        }
    }
}
