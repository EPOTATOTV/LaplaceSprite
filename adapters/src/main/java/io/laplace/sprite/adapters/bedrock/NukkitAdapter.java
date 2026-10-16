package io.laplace.sprite.adapters.bedrock;

import io.laplace.sprite.adapters.AbstractLaplaceAdapter;
import io.laplace.sprite.adapters.AdapterStatus;
import io.laplace.sprite.adapters.PlatformFacts;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.spi.SupportRange;

import java.util.List;

/** Nukkit（14.2）：基岩服务端，plugin.yml 的 api 字段是版本列表而非标量。 */
public final class NukkitAdapter extends AbstractLaplaceAdapter {

    private static final PlatformFacts FACTS = new PlatformFacts(
            "Nukkit",
            "构建 plugins/ 目录，plugin.yml 由 Nukkit 装载器读取",
            "plugin.yml（api 为版本列表）",
            "Nukkit 主线程 tick（20TPS）",
            AdapterStatus.IMPLEMENTED,
            List.of("api 字段是列表（如 [\"1.0.0\",\"1.0.1\"]），解析时要与 Bukkit 的 api-version 标量区分",
                    "基岩版没有 brand + mod 哈希，身份用附加组件（ADDON）表达",
                    "坐标与命中体积口径与 Java 版一致，可直接复用移动类断言"));

    public NukkitAdapter() {
        super("nukkit", PlatformFamily.NUKKIT, SupportRange.minecraft("1.20", null), FACTS);
    }
}
