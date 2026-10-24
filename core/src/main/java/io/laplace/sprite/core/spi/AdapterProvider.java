package io.laplace.sprite.core.spi;

import java.util.List;

/**
 * 第三方适配器注入点（v3.0 L3-01，YouerAdapter 模式）。
 *
 * <p>外部模块只要在 {@code META-INF/services/io.laplace.sprite.core.spi.AdapterProvider}
 * 里登记实现，就能把自己的适配器挂进内置注册表，内核代码不用改一行。</p>
 */
public interface AdapterProvider {

    String providerId();

    List<LaplaceAdapter> adapters();
}
