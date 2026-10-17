package io.laplace.sprite.cheats;

import io.laplace.sprite.core.model.CheatType;
import io.laplace.sprite.core.model.SampleTier;
import io.laplace.sprite.core.model.SampleTierName;
import io.laplace.sprite.core.model.TestSpec;

import java.util.List;
import java.util.Optional;

/**
 * 样本库条目：core 的 {@link TestSpec} 加上类别与样本类型。
 *
 * <p>{@code TestSpec} 定义在 core 模块，cheats 不改动该模块，因此分类元数据由本记录承载；
 * 需要交给 core（会话、检测上下文）时用 {@link #spec()} 转回。</p>
 *
 * @param id         样本 id
 * @param name       展示名
 * @param category   样本类别
 * @param sampleType 稳定的样本类型 id；作弊样本取 {@link CheatType} 名，其余取大写下划线 id
 * @param cheatType  作弊子类型；仅 CHEAT 非空
 * @param target     被测目标 id
 * @param tiers      配置档位
 * @param baseline   合法基线引用
 * @param injector   注入器 id
 * @param source     样本来源
 * @param notes      备注
 */
public record SampleSpec(
        String id,
        String name,
        SampleCategory category,
        String sampleType,
        CheatType cheatType,
        String target,
        List<SampleTier> tiers,
        String baseline,
        String injector,
        String source,
        String notes) {

    public SampleSpec {
        tiers = tiers == null ? List.of() : List.copyOf(tiers);
    }

    public boolean isCheat() {
        return category == SampleCategory.CHEAT;
    }

    public Optional<SampleTier> tier(SampleTierName name) {
        return tiers.stream().filter(tier -> tier.tier() == name).findFirst();
    }

    public List<SampleTierName> declaredTiers() {
        return tiers.stream().map(SampleTier::tier).toList();
    }

    /** 转回 core 的样本定义；非作弊样本的 type 为空。 */
    public TestSpec spec() {
        return new TestSpec(id, name, cheatType, target, tiers, baseline, injector, source, notes);
    }

    /** 只有作弊样本能无损转回 {@link TestSpec} 的旧口径。 */
    public static SampleSpec of(TestSpec spec) {
        String sampleType = spec.type() == null ? "UNKNOWN" : spec.type().name();
        return new SampleSpec(spec.id(), spec.name(), SampleCategory.CHEAT, sampleType, spec.type(),
                spec.target(), spec.tiers(), spec.baseline(), spec.injector(), spec.source(), spec.notes());
    }
}
