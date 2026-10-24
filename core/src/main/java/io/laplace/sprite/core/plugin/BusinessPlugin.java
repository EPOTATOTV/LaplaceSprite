package io.laplace.sprite.core.plugin;

/**
 * 业务插件契约：业务模块以插件身份接入内核，而不是被内核反向编译期依赖。
 *
 * <p>实现类通过 {@code META-INF/services/io.laplace.sprite.core.plugin.BusinessPlugin}
 * 注册，由 {@link PluginRuntime} 发现并激活。</p>
 */
public interface BusinessPlugin {

    PluginDescriptor descriptor();

    /** 激活时调用，插件在这里订阅内核钩子。 */
    void onLoad(PluginContext context);

    /** 卸载时调用，默认什么都不做。 */
    default void onUnload() {
    }
}
