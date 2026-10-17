package io.laplace.sprite.cheats;

import io.laplace.sprite.core.model.CheatSpec;
import io.laplace.sprite.core.model.CheatTierName;
import io.laplace.sprite.core.model.CheatType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 作弊矩阵（4.2）：样本 × 档位的组合视图，样本来自 {@link CheatCatalog}。
 */
public final class CheatMatrix {

    private final CheatCatalog catalog;

    public CheatMatrix() {
        this(new CheatCatalog());
    }

    public CheatMatrix(CheatCatalog catalog) {
        this.catalog = catalog;
    }

    public List<CheatSpec> specs() {
        return catalog.loadDefaults();
    }

    /** 按作弊类型分组；未出现的类型映射为空列表。 */
    public Map<CheatType, List<CheatSpec>> byType() {
        Map<CheatType, List<CheatSpec>> grouped = new EnumMap<>(CheatType.class);
        for (CheatType type : CheatType.values()) {
            grouped.put(type, new ArrayList<>());
        }
        for (CheatSpec spec : specs()) {
            grouped.get(spec.type()).add(spec);
        }
        Map<CheatType, List<CheatSpec>> result = new EnumMap<>(CheatType.class);
        grouped.forEach((type, list) -> result.put(type, List.copyOf(list)));
        return Collections.unmodifiableMap(result);
    }

    public List<CheatTierName> tiers() {
        return List.of(CheatTierName.values());
    }

    /** 配置总数 = 各样本已声明档位数量之和。 */
    public int totalConfigurations() {
        return specs().stream().mapToInt(spec -> spec.tiers().size()).sum();
    }
}
