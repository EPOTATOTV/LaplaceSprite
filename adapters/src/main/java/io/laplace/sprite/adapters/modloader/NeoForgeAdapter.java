package io.laplace.sprite.adapters.modloader;

import io.laplace.sprite.adapters.AbstractLaplaceAdapter;
import io.laplace.sprite.adapters.AdapterStatus;
import io.laplace.sprite.adapters.PlatformFacts;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.spi.SupportRange;

import java.util.List;

/** NeoForge（14.3）：从 Forge 分叉出来的现代分支，加载器版本自带 Minecraft 版本号。 */
public final class NeoForgeAdapter extends AbstractLaplaceAdapter {

    private static final PlatformFacts FACTS = new PlatformFacts(
            "NeoForge",
            "构建 mods/ 目录，模组元数据在 META-INF/neoforge.mods.toml",
            "META-INF/neoforge.mods.toml",
            "NeoForge 主线程 tick + 异步事件总线",
            AdapterStatus.IMPLEMENTED,
            List.of("加载器版本形如 21.11.0，前两段即 Minecraft 版本（1.21.11），矩阵拉取按此反推",
                    "mods.toml 字段与 Forge 同构，复用同一解析器",
                    "版本矩阵缺失时按兼容链回退 Forge 并输出降级告警",
                    "MC 26.x 线预留列位，矩阵先占位不铺用例"));

    private static final SupportRange RANGE = new SupportRange("1.20.2", null, "20.2", null);

    public NeoForgeAdapter() {
        super("neoforge", PlatformFamily.NEOFORGE, RANGE, FACTS);
    }
}
