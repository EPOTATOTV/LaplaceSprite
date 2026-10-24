package io.laplace.sprite.report;

import io.laplace.sprite.core.plugin.BusinessPlugin;
import io.laplace.sprite.core.plugin.KernelHook;
import io.laplace.sprite.core.plugin.PluginContext;
import io.laplace.sprite.core.plugin.PluginDescriptor;
import io.laplace.sprite.core.plugin.TestSuiteProvider;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PACC 业务插件（v3.0 L3-05）。
 *
 * <p>除订阅测试生命周期外，还声明 PACC 独有的 cheat / mixed / boundary 套件，CLI 据此在插件被排除时
 * 拒绝这些套件，而 legit 走内核自带仿真不受影响。</p>
 */
public final class PaccBusinessPlugin implements BusinessPlugin, TestSuiteProvider {

    public static final String PLUGIN_ID = "pacc";

    private final AtomicInteger observedLifecycleEvents = new AtomicInteger();
    private AutoCloseable subscription;

    @Override
    public PluginDescriptor descriptor() {
        return new PluginDescriptor(PLUGIN_ID, "PACC 夜间基准", "1.5.0", List.of(),
                List.of(PLUGIN_ID, "test-suites"));
    }

    @Override
    public List<String> suiteIds() {
        return List.of("cheat", "mixed", "boundary");
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
