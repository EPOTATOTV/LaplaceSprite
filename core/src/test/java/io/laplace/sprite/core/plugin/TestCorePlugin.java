package io.laplace.sprite.core.plugin;

import java.util.List;

/** ServiceLoader 发现用的测试插件。 */
public final class TestCorePlugin implements BusinessPlugin {

    @Override
    public PluginDescriptor descriptor() {
        return new PluginDescriptor("core-test", "测试插件", "1.0.0", List.of(), List.of("core-test"));
    }

    @Override
    public void onLoad(PluginContext context) {
    }
}
