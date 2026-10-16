package io.laplace.sprite.adapters.hybrid;

import io.laplace.sprite.adapters.AdapterStatus;
import io.laplace.sprite.adapters.PlatformFacts;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.spi.SupportRange;

import java.util.List;

/** Cardboard（14.3）：Bukkit × Fabric 组合适配器，处于仅验证状态，行为矩阵未开放。 */
public final class CardboardAdapter extends AbstractHybridAdapter {

    private static final SupportRange RANGE = SupportRange.minecraft("1.20.1", null);

    private static final PlatformFacts FACTS = new PlatformFacts(
            "Cardboard",
            "双目录合并：Bukkit 插件落 plugins/，Fabric 模组落 mods/",
            "plugin.yml + fabric.mod.json",
            "Bukkit Scheduler 主线程 tick + Fabric 事件总线（双总线，重复事件折叠）",
            AdapterStatus.COMPAT_SHELL,
            HybridPitfalls.notes(List.of(
                    "仅验证：只确认组合适配层能装配与归一流，不承诺真实服务端行为矩阵",
                    "Fabric 侧无官方 Connector 支持，跨生态 ID 映射校验是这里最可能先暴露的缺口")));

    public CardboardAdapter() {
        super("cardboard", PlatformFamily.CARDBOARD, RANGE, FACTS,
                HybridHosts.bukkitHost(), HybridHosts.loaderFor(PlatformFamily.CARDBOARD));
    }
}
