package io.laplace.sprite.adapters.bukkit;

import io.laplace.sprite.adapters.AbstractLaplaceAdapter;
import io.laplace.sprite.adapters.AdapterStatus;
import io.laplace.sprite.adapters.PlatformFacts;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.spi.SupportRange;

import java.util.List;

/** Purpur（14.2）：兼容外壳，超出支持区间时按 Purpur → Paper → Bukkit 回退。 */
public final class PurpurAdapter extends AbstractLaplaceAdapter {

    private static final PlatformFacts FACTS = new PlatformFacts(
            "Purpur",
            "构建 plugins/ 目录，装载顺序与 Paper 一致",
            "plugin.yml",
            "Bukkit Scheduler（Purpur 未改变调度模型）",
            AdapterStatus.COMPAT_SHELL,
            List.of("仅 ConfigurableScheduler 与部分实体行为不同，事件桥复用 Paper",
                    "版本超出区间时回退 Paper 并输出降级告警",
                    "断言只看归一化流，不因外壳而放宽阈值"));

    public PurpurAdapter() {
        super("purpur", PlatformFamily.PURPUR, SupportRange.minecraft("1.16.5", "1.21.11"), FACTS);
    }
}
