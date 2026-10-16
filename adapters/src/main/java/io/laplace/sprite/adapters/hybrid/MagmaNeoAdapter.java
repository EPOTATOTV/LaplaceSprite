package io.laplace.sprite.adapters.hybrid;

import io.laplace.sprite.adapters.AdapterStatus;
import io.laplace.sprite.adapters.PlatformFacts;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.spi.SupportRange;

import java.util.List;

/** Magma-Neo（14.3）：Bukkit × NeoForge 组合适配器，矩阵以 1.21.1 为基准档。 */
public final class MagmaNeoAdapter extends AbstractHybridAdapter {

    private static final SupportRange RANGE = SupportRange.minecraft("1.21.1", null);

    private static final PlatformFacts FACTS = new PlatformFacts(
            "Magma-Neo",
            "双目录合并：Bukkit 插件落 plugins/，NeoForge 模组落 mods/",
            "plugin.yml + META-INF/neoforge.mods.toml",
            "Bukkit Scheduler 主线程 tick + NeoForge 事件总线（双总线，重复事件折叠）",
            AdapterStatus.IMPLEMENTED,
            HybridPitfalls.notes(List.of(
                    "与 Youer 同为 Bukkit × NeoForge，实现不同，矩阵不能因为家族相同就只测一个",
                    "Connector 注入的模组类与插件类并存，命令树冲突要单独铺用例")));

    public MagmaNeoAdapter() {
        super("magmaneo", PlatformFamily.MAGMA_NEO, RANGE, FACTS,
                HybridHosts.bukkitHost(), HybridHosts.loaderFor(PlatformFamily.MAGMA_NEO));
    }
}
