package io.laplace.sprite.adapters;

import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.spi.SupportRange;

import java.util.List;

/** 第三方注入测试用适配器，只通过 provider 出现在注册表里。 */
public final class TestProviderAdapter extends AbstractLaplaceAdapter {

    public TestProviderAdapter(String id) {
        super(id, PlatformFamily.VANILLA_DATAPACK, SupportRange.any(),
                new PlatformFacts("第三方测试平台", "测试装配", "test.yml", "同步",
                        AdapterStatus.IMPLEMENTED_TIER1, List.of("仅用于注入测试")));
    }
}
