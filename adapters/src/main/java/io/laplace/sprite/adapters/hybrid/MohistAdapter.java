package io.laplace.sprite.adapters.hybrid;

import io.laplace.sprite.adapters.AdapterStatus;
import io.laplace.sprite.adapters.PlatformFacts;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.spi.SupportRange;

import java.util.List;

/**
 * Mohist（升级实施文档 v2 L2-10）：Bukkit × Forge 组合适配器，覆盖 1.21.1 到 26.3。
 *
 * <p>与 Youer 同一模式：双目录、双事件总线、跨生态 ID 校验。26.x 起客户端默认图形 API
 * 切到 Vulkan，服务端事件与方块语义不受影响，但冒烟仍需记录客户端渲染差异。</p>
 */
public final class MohistAdapter extends AbstractHybridAdapter {

    private static final SupportRange RANGE = SupportRange.minecraft("1.21.1", null);

    private static final PlatformFacts FACTS = new PlatformFacts(
            "Mohist",
            "双目录合并：Bukkit 插件落 plugins/，Forge 模组落 mods/",
            "plugin.yml + META-INF/mods.toml",
            "Bukkit Scheduler 主线程 tick + Forge 事件总线（双总线，重复事件折叠）",
            AdapterStatus.IMPLEMENTED,
            HybridPitfalls.notes(List.of(
                    "Forge 侧 Mixin 与 Bukkit 插件 ClassLoader 隔离不同，类阴影问题优先核对",
                    "1.21.1 与 26.3 两档都要进矩阵；26.4 客户端 Vulkan 默认化只影响渲染冒烟")));

    public MohistAdapter() {
        super("mohist", PlatformFamily.MOHIST, RANGE, FACTS,
                HybridHosts.bukkitHost(), HybridHosts.loaderFor(PlatformFamily.MOHIST));
    }
}
