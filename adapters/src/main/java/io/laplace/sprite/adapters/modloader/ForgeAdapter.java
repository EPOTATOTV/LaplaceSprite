package io.laplace.sprite.adapters.modloader;

import io.laplace.sprite.adapters.AbstractLaplaceAdapter;
import io.laplace.sprite.adapters.AdapterStatus;
import io.laplace.sprite.adapters.PlatformFacts;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.spi.SupportRange;

import java.util.List;

/** Forge（14.3）：传统分支，描述符是 META-INF/mods.toml。 */
public final class ForgeAdapter extends AbstractLaplaceAdapter {

    private static final PlatformFacts FACTS = new PlatformFacts(
            "Forge（1.20.1 LTS + 现代并行线）",
            "构建 mods/ 目录，模组元数据在 META-INF/mods.toml",
            "META-INF/mods.toml",
            "Forge 主线程 tick + 并行能力（ParallelDispatch）",
            AdapterStatus.IMPLEMENTED,
            List.of("[[dependencies.<modId>]] 的 mandatory 决定硬/软，versionRange 只归档不求解",
                    "ordering（BEFORE/AFTER/NONE）影响装载顺序断言",
                    "GameTest 套件结果经 GAME_TEST_SUITE 事件参与断言",
                    "1.20.1 仍为 LTS 长期支持分支，与现代并行线并存，矩阵两档都要铺"));

    public ForgeAdapter() {
        super("forge", PlatformFamily.FORGE, SupportRange.any(), FACTS);
    }
}
