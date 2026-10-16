package io.laplace.sprite.adapters.sponge;

import io.laplace.sprite.adapters.AbstractLaplaceAdapter;
import io.laplace.sprite.adapters.AdapterStatus;
import io.laplace.sprite.adapters.PlatformFacts;
import io.laplace.sprite.core.model.PlatformFamily;

import java.util.List;

/** SpongeVanilla（14.3）：接原版服务端，插件与模组同放 mods/，无宿主加载器可回退。 */
public final class SpongeVanillaAdapter extends AbstractLaplaceAdapter {

    private static final PlatformFacts FACTS = new PlatformFacts(
            "SpongeVanilla",
            SpongeAdapterSupport.ASSEMBLY,
            SpongeAdapterSupport.DESCRIPTOR,
            SpongeAdapterSupport.SCHEDULER,
            AdapterStatus.IMPLEMENTED_TIER1,
            SpongeAdapterSupport.notes(List.of(
                    "兼容链为空：SpongeVanilla 之下没有加载器宿主，版本矩阵不匹配只能报无可用适配器")));

    public SpongeVanillaAdapter() {
        super("spongevanilla", PlatformFamily.SPONGE_VANILLA, SpongeAdapterSupport.RANGE, FACTS);
    }
}
