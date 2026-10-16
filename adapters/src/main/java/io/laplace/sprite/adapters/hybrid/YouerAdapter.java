package io.laplace.sprite.adapters.hybrid;

import io.laplace.sprite.adapters.AdapterStatus;
import io.laplace.sprite.adapters.PlatformFacts;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.spi.SupportRange;

import java.util.List;

/** Youer（14.3）：Bukkit × NeoForge 组合适配器，第一梯队，覆盖 1.21.1 与 26.2。 */
public final class YouerAdapter extends AbstractHybridAdapter {

    private static final SupportRange RANGE = SupportRange.minecraft("1.21.1", null);

    private static final PlatformFacts FACTS = new PlatformFacts(
            "Youer",
            "双目录合并：Bukkit 插件落 plugins/，NeoForge 模组落 mods/",
            "plugin.yml + META-INF/neoforge.mods.toml",
            "Bukkit Scheduler 主线程 tick + NeoForge 事件总线（双总线，重复事件折叠）",
            AdapterStatus.IMPLEMENTED,
            HybridPitfalls.notes(List.of(
                    "第一梯队混合端：1.21.1 与 26.2 两档都要进矩阵，跨大版本组合不能只测一档",
                    "NeoForge 侧事件总线与 Bukkit Listener 需分别归一后再折叠，顺序不一致也算双发")));

    public YouerAdapter() {
        super("youer", PlatformFamily.YOUER, RANGE, FACTS,
                HybridHosts.bukkitHost(), HybridHosts.loaderFor(PlatformFamily.YOUER));
    }
}
