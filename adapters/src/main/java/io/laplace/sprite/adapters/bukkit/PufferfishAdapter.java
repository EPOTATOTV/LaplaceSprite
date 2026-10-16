package io.laplace.sprite.adapters.bukkit;

import io.laplace.sprite.adapters.AbstractLaplaceAdapter;
import io.laplace.sprite.adapters.AdapterStatus;
import io.laplace.sprite.adapters.PlatformFacts;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.spi.SupportRange;

import java.util.List;

/** Pufferfish（14.2）：兼容外壳，重点是性能优化项对指标口径的影响。 */
public final class PufferfishAdapter extends AbstractLaplaceAdapter {

    private static final PlatformFacts FACTS = new PlatformFacts(
            "Pufferfish",
            "构建 plugins/ 目录，装载顺序与 Paper 一致",
            "plugin.yml",
            "Bukkit Scheduler（含异步实体追踪优化）",
            AdapterStatus.COMPAT_SHELL,
            List.of("优化项会改变 MSPT 基线，性能门禁需用同平台基线对照",
                    "版本超出区间时回退 Paper 并输出降级告警",
                    "事件桥不做平台特化，直接复用 Bukkit 家族实现"));

    public PufferfishAdapter() {
        super("pufferfish", PlatformFamily.PUFFERFISH, SupportRange.minecraft("1.18", "1.21.11"), FACTS);
    }
}
