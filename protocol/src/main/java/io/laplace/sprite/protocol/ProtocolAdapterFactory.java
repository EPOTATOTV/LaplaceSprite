package io.laplace.sprite.protocol;

import io.laplace.sprite.core.spi.LaplaceAdapter;
import io.laplace.sprite.protocol.adapter.ViaProxyAdapter;

import java.util.List;

/**
 * 协议轨的模块级适配器工厂（15.6）。
 *
 * <p>不改动 adapters 模块的注册表，先在这里暴露；CLI 侧组装时再登记进去。</p>
 */
public final class ProtocolAdapterFactory {

    private ProtocolAdapterFactory() {
    }

    /** ViaProxy 协议代理适配器。 */
    public static ViaProxyAdapter viaProxy() {
        return new ViaProxyAdapter();
    }

    /** 本模块对外暴露的全部适配器。 */
    public static List<LaplaceAdapter> adapters() {
        return List.of(viaProxy());
    }
}
