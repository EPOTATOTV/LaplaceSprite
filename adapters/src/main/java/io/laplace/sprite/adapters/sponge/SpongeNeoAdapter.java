package io.laplace.sprite.adapters.sponge;

import io.laplace.sprite.adapters.AbstractLaplaceAdapter;
import io.laplace.sprite.adapters.AdapterStatus;
import io.laplace.sprite.adapters.PlatformFacts;
import io.laplace.sprite.core.model.PlatformFamily;

import java.util.List;

/** SpongeNeo（14.3）：接 NeoForge，同一服务端可同时跑 NeoForge 模组与 Sponge 插件。 */
public final class SpongeNeoAdapter extends AbstractLaplaceAdapter {

    private static final PlatformFacts FACTS = new PlatformFacts(
            "SpongeNeo",
            SpongeAdapterSupport.ASSEMBLY,
            SpongeAdapterSupport.DESCRIPTOR,
            SpongeAdapterSupport.SCHEDULER,
            AdapterStatus.IMPLEMENTED_TIER1,
            SpongeAdapterSupport.notes(List.of(
                    "NeoForge 模组与 Sponge 插件共处 mods/，两套事件总线（NeoForge 事件 + @Listener）须分别归一再合并",
                    "版本矩阵缺失时按兼容链回退 NeoForge 并输出降级告警")));

    public SpongeNeoAdapter() {
        super("spongeneo", PlatformFamily.SPONGE_NEO, SpongeAdapterSupport.RANGE, FACTS);
    }
}
