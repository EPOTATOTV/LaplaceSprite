package io.laplace.sprite.adapters.bukkit;

import io.laplace.sprite.adapters.AbstractLaplaceAdapter;
import io.laplace.sprite.adapters.AdapterStatus;
import io.laplace.sprite.adapters.PlatformFacts;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.spi.SupportRange;

import java.util.List;

/** Bukkit（14.2）：家族基线适配器，其余 Bukkit 系平台都从它的 API 形态出发。 */
public final class BukkitAdapter extends AbstractLaplaceAdapter {

    private static final PlatformFacts FACTS = new PlatformFacts(
            "Bukkit",
            "构建 plugins/ 目录，plugin.yml 由装载器按 depend / softdepend / loadbefore 顺序装载",
            "plugin.yml",
            "Bukkit Scheduler（主线程单 tick 串行）",
            AdapterStatus.IMPLEMENTED,
            List.of("事件桥至少归一出 10 类核心事件：PlayerJoin/Leave、Move、Interact、Inventory、Chat、Packet 等",
                    "主线程同步事件按 tick 顺序进流，不做重排",
                    "异步事件（AsyncChatEvent 一类）先进缓冲，下一 tick 收口后并入同一流"));

    public BukkitAdapter() {
        super("bukkit", PlatformFamily.BUKKIT, SupportRange.any(), FACTS);
    }
}
