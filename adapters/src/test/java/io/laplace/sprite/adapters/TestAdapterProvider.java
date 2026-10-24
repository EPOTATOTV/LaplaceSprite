package io.laplace.sprite.adapters;

import io.laplace.sprite.core.spi.AdapterProvider;
import io.laplace.sprite.core.spi.LaplaceAdapter;

import java.util.List;

/** 第三方适配器注入的测试 provider：一个新增 id，一个与内置冲突的 id。 */
public final class TestAdapterProvider implements AdapterProvider {

    @Override
    public String providerId() {
        return "test-provider";
    }

    @Override
    public List<LaplaceAdapter> adapters() {
        return List.of(new TestProviderAdapter("test-provider"), new TestProviderAdapter("paper"));
    }
}
