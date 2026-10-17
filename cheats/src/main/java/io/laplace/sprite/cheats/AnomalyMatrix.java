package io.laplace.sprite.cheats;

import io.laplace.sprite.core.model.CheatType;
import io.laplace.sprite.core.model.SampleTierName;
import io.laplace.sprite.core.model.TestSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 异常样本矩阵（4.2）：样本 × 档位的组合视图，样本来自 {@link CheatCatalog}。
 *
 * <p>{@link #specs()} / {@link #byType()} / {@link #totalConfigurations()} 维持作弊子类口径，
 * 供既有的检出率门禁与 CLI 概览使用；{@link #samples()} / {@link #byCategory()} 才能看到全量矩阵。</p>
 */
public final class AnomalyMatrix {

    private final CheatCatalog catalog;

    public AnomalyMatrix() {
        this(new CheatCatalog());
    }

    public AnomalyMatrix(CheatCatalog catalog) {
        this.catalog = catalog;
    }

    /** 作弊子类样本，保持旧入口语义。 */
    public List<TestSpec> specs() {
        return catalog.cheatSpecs();
    }

    /** 全量样本，含内容 / 科技 / 性能 / 网络 / 异常类别。 */
    public List<SampleSpec> samples() {
        return catalog.allSamples();
    }

    /** 按作弊类型分组；未出现的类型映射为空列表。 */
    public Map<CheatType, List<TestSpec>> byType() {
        Map<CheatType, List<TestSpec>> grouped = new EnumMap<>(CheatType.class);
        for (CheatType type : CheatType.values()) {
            grouped.put(type, new ArrayList<>());
        }
        for (TestSpec spec : specs()) {
            grouped.get(spec.type()).add(spec);
        }
        Map<CheatType, List<TestSpec>> result = new EnumMap<>(CheatType.class);
        grouped.forEach((type, list) -> result.put(type, List.copyOf(list)));
        return Collections.unmodifiableMap(result);
    }

    /** 按类别分组，顺序同 {@link SampleCategory}。 */
    public Map<SampleCategory, List<SampleSpec>> byCategory() {
        Map<SampleCategory, List<SampleSpec>> grouped = new LinkedHashMap<>();
        for (SampleCategory category : SampleCategory.values()) {
            grouped.put(category, List.copyOf(catalog.byCategory(category)));
        }
        return Collections.unmodifiableMap(grouped);
    }

    public List<SampleTierName> tiers() {
        return List.of(SampleTierName.values());
    }

    /** 作弊配置总数 = 各作弊样本已声明档位数量之和。 */
    public int totalConfigurations() {
        return specs().stream().mapToInt(spec -> spec.tiers().size()).sum();
    }
}
