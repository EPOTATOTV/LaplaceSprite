package io.laplace.sprite.core.model;

import java.util.List;
import java.util.Optional;

/**
 * 作弊样本定义（第五章 CheatSpec），YAML/JSON 声明式。
 *
 * @param id       样本 id（如 {@code speed.wurst.max}）
 * @param name     展示名
 * @param type     作弊类型
 * @param target   检测目标（RunTarget id，如 {@code detection-engine}）
 * @param tiers    配置档位
 * @param baseline 合法基线引用（LegitTrajectory id）；空表示使用默认基线
 * @param injector 注入器 id（注册在 CheatInjectorRegistry）
 * @param source   样本来源（开源客户端名，如 Wurst / LiquidBounce）
 * @param notes    备注
 */
public record CheatSpec(
        String id,
        String name,
        CheatType type,
        String target,
        List<CheatTier> tiers,
        String baseline,
        String injector,
        String source,
        String notes) {

    public CheatSpec {
        tiers = tiers == null ? List.of() : List.copyOf(tiers);
    }

    public Optional<CheatTier> tier(CheatTierName name) {
        return tiers.stream().filter(t -> t.tier() == name).findFirst();
    }

    public List<CheatTierName> tierNames() {
        return tiers.stream().map(CheatTier::tier).toList();
    }
}
