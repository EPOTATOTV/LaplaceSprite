package io.laplace.sprite.adapters.modloader;

import io.laplace.sprite.adapters.AbstractLaplaceAdapter;
import io.laplace.sprite.adapters.AdapterStatus;
import io.laplace.sprite.adapters.PlatformFacts;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.spi.SupportRange;

import java.util.List;

/** Quilt（14.3）：与 Fabric 生态高度重叠，元数据在 quilt.mod.json 的 quilt_loader 块。 */
public final class QuiltAdapter extends AbstractLaplaceAdapter {

    private static final PlatformFacts FACTS = new PlatformFacts(
            "Quilt（QSL 已停止维护，差异化仅剩 Fabric 兼容验证）",
            "构建 mods/ 目录，模组元数据在 quilt.mod.json（schema_version 1）",
            "quilt.mod.json",
            "Quilt 主线程 tick + mixin",
            AdapterStatus.COMPAT_SHELL,
            List.of("quilt_loader 块的 group:id 共同构成模组标识",
                    "加载器版本低于区间下界时按兼容链回退 Fabric 并输出降级告警",
                    "QSL（Quilt Standard Libraries）已停止维护，Quilt 与 Fabric 的差异只剩兼容验证，实现按兼容复用处理"));

    private static final SupportRange RANGE = new SupportRange(null, null, "0.26.0", null);

    public QuiltAdapter() {
        super("quilt", PlatformFamily.QUILT, RANGE, FACTS);
    }
}
