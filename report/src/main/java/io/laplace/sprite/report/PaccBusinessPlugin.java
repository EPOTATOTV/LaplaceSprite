package io.laplace.sprite.report;

import io.laplace.sprite.core.plugin.BusinessPlugin;
import io.laplace.sprite.core.plugin.KernelHook;
import io.laplace.sprite.core.plugin.PluginContext;
import io.laplace.sprite.core.plugin.PluginDescriptor;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PACC 业务插件（v3.0 L3-01 最小闭环）。
 *
 * <p>只订阅测试生命周期钩子并计数，真正的回归编排在 L3-05 展开。插件身份让 CLI 能通过
 * {@code laplace.plugins.exclude} 关掉 PACC 而不影响其他业务。</p>
 */
public final class PaccBusinessPlugin implements BusinessPlugin {

    public static final String PLUGIN_ID = "pacc";

    private final AtomicInteger observedLifecycleEvents = new AtomicInteger();
    private AutoCloseable subscription;

    @Override
    public PluginDescriptor descriptor() {
        return new PluginDescriptor(PLUGIN_ID, "PACC 夜间基准", "1.5.0", List.of(), List.of(PLUGIN_ID));
    }

    @Override
    public void onLoad(PluginContext context) {
        subscription = context.events().subscribe(KernelHook.TEST_LIFECYCLE,
                event -> observedLifecycleEvents.incrementAndGet());
    }

    @Override
    public void onUnload() {
        if (subscription != null) {
            try {
                subscription.close();
            } catch (Exception ex) {
                throw new IllegalStateException("PACC 插件退订失败", ex);
            }
            subscription = null;
        }
    }

    /** 已收到的测试生命周期事件数，供测试与诊断用。 */
    public int observedLifecycleEvents() {
        return observedLifecycleEvents.get();
    }
}
