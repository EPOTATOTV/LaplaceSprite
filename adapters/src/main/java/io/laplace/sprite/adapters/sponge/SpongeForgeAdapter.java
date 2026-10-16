package io.laplace.sprite.adapters.sponge;

import io.laplace.sprite.adapters.AbstractLaplaceAdapter;
import io.laplace.sprite.adapters.AdapterStatus;
import io.laplace.sprite.adapters.PlatformFacts;
import io.laplace.sprite.core.model.PlatformFamily;

import java.util.List;

/** SpongeForge（14.3）：接 Forge，同一 JAR 既是 Forge 模组又是 Sponge 插件，识别顺序有讲究。 */
public final class SpongeForgeAdapter extends AbstractLaplaceAdapter {

    private static final PlatformFacts FACTS = new PlatformFacts(
            "SpongeForge",
            SpongeAdapterSupport.ASSEMBLY,
            SpongeAdapterSupport.DESCRIPTOR,
            SpongeAdapterSupport.SCHEDULER,
            AdapterStatus.IMPLEMENTED_TIER1,
            SpongeAdapterSupport.notes(List.of(
                    "同一 JAR 同时是 Forge 模组：识别须先查 sponge_plugins.json，缺失才回退 forge 元数据",
                    "版本矩阵缺失时按兼容链回退 Forge 并输出降级告警")));

    public SpongeForgeAdapter() {
        super("spongeforge", PlatformFamily.SPONGE_FORGE, SpongeAdapterSupport.RANGE, FACTS);
    }
}
