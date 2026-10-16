package io.laplace.sprite.adapters.bukkit;

import io.laplace.sprite.adapters.AbstractLaplaceAdapter;
import io.laplace.sprite.adapters.AdapterStatus;
import io.laplace.sprite.adapters.PlatformFacts;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.spi.SupportRange;

import java.util.List;

/** Spigot（14.2）：Bukkit API 的优化分支，异步区块加载等行为差异体现在性能测试轨。 */
public final class SpigotAdapter extends AbstractLaplaceAdapter {

    private static final PlatformFacts FACTS = new PlatformFacts(
            "Spigot",
            "构建 plugins/ 目录，spigot.yml 影响实体激活范围与异步区块加载",
            "plugin.yml",
            "Bukkit Scheduler（主线程单 tick 串行）",
            AdapterStatus.IMPLEMENTED,
            List.of("实体激活范围（entity-activation-range）会稀释远距离模拟玩家的事件密度",
                    "异步区块加载下 ChunkLoad 事件可能晚于 Move 到达，断言层不依赖两者顺序",
                    "TPS 与 MSPT 取自 tick 事件，与 Paper 口径一致"));

    public SpigotAdapter() {
        super("spigot", PlatformFamily.SPIGOT, SupportRange.any(), FACTS);
    }
}
