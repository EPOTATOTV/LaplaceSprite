package io.laplace.sprite.adapters.proxy;

import io.laplace.sprite.adapters.AbstractLaplaceAdapter;
import io.laplace.sprite.adapters.AdapterStatus;
import io.laplace.sprite.adapters.PlatformFacts;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.spi.SupportRange;

import java.util.List;

/** Waterfall（14.2）：已停止维护，只保留兼容外壳，矩阵不匹配时回退 BungeeCord。 */
public final class WaterfallAdapter extends AbstractLaplaceAdapter {

    private static final PlatformFacts FACTS = new PlatformFacts(
            "Waterfall",
            "构建 plugins/ 目录，bungee.yml 与 BungeeCord 同构",
            "bungee.yml",
            "BungeeCord 单线程事件循环",
            AdapterStatus.EOL,
            List.of("上游已停止维护，仅覆盖到 1.20.4",
                    "超出区间一律回退 BungeeCord 并输出降级告警",
                    "断言与 BungeeCord 完全共用，不做单独口径"));

    public WaterfallAdapter() {
        super("waterfall", PlatformFamily.WATERFALL, SupportRange.minecraft("1.13", "1.20.4"), FACTS);
    }
}
