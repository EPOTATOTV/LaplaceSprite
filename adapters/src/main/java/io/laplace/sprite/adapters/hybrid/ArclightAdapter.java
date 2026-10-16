package io.laplace.sprite.adapters.hybrid;

import io.laplace.sprite.adapters.AdapterStatus;
import io.laplace.sprite.adapters.PlatformFacts;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.spi.SupportRange;

import java.util.List;

/** Arclight（14.3）：Bukkit × 模组加载器组合适配器，第一梯队；按加载器分三变体。 */
public final class ArclightAdapter extends AbstractHybridAdapter {

    private static final SupportRange RANGE = SupportRange.minecraft("1.21.1", null);

    private final ArclightVariant variant;

    public ArclightAdapter() {
        this(ArclightVariant.FORGE);
    }

    public ArclightAdapter(ArclightVariant variant) {
        super(variant.adapterId(), PlatformFamily.ARCLIGHT, RANGE, factsFor(variant),
                HybridHosts.bukkitHost(), variant.loaderFamily());
        this.variant = variant;
    }

    public ArclightVariant variant() {
        return variant;
    }

    /** 三变体作为数据暴露，矩阵据此铺组合。 */
    public static List<ArclightVariant> variants() {
        return List.of(ArclightVariant.values());
    }

    private static PlatformFacts factsFor(ArclightVariant variant) {
        return new PlatformFacts(
                "Arclight · " + variant.name(),
                "双目录合并：Bukkit 插件落 plugins/，模组落 mods/",
                "plugin.yml + " + variant.modDescriptor(),
                "Bukkit Scheduler 主线程 tick + " + variant.loaderFamily()
                        + " 事件总线（双总线，重复事件折叠）",
                AdapterStatus.IMPLEMENTED,
                HybridPitfalls.notes(List.of(
                        "Arclight 1.21.1 三变体（Forge / NeoForge / Fabric）共用同一 Bukkit 侧，仅模组侧总线不同",
                        "第一梯队混合端：矩阵用例须覆盖事件双发、类加载顺序、命令树冲突与插件原版语义假设落空")));
    }
}
