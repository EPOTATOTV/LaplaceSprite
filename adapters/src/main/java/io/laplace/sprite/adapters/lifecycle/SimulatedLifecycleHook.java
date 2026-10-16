package io.laplace.sprite.adapters.lifecycle;

import io.laplace.sprite.adapters.deps.DependencyOrderer;
import io.laplace.sprite.adapters.matrix.VersionMatrixRegistry;
import io.laplace.sprite.core.spi.InstallContext;
import io.laplace.sprite.core.spi.LifecycleHook;
import io.laplace.sprite.core.spi.TickContext;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 生命周期钩子（14.4 ③ / 14.5）：记录冷启动序列 onLoad → onEnable → onTick → onShutdown。
 *
 * <p>只走冷启动语义，不提供 /reload 热加载路径：onLoad 里做依赖与版本矩阵校验，
 * 校验没过就不允许进 onEnable。历史列表即验收证据「生命周期日志」。</p>
 */
public final class SimulatedLifecycleHook implements LifecycleHook {

    private static final int TICK_HISTORY_LIMIT = 20;

    private final DependencyOrderer orderer = new DependencyOrderer();
    private final VersionMatrixRegistry matrix;
    private final List<String> history = new CopyOnWriteArrayList<>();
    private volatile boolean loaded;
    private int tickRecords;

    public SimulatedLifecycleHook() {
        this(null);
    }

    /** matrix 为 null 时跳过版本矩阵校验，只做依赖校验。 */
    public SimulatedLifecycleHook(VersionMatrixRegistry matrix) {
        this.matrix = matrix;
    }

    @Override
    public void onLoad(InstallContext ctx) {
        try {
            int ordered = orderer.order(ctx.artifacts()).size();
            if (matrix != null && ctx.platform() != null && !matrix.supports(ctx.platform())) {
                throw new IllegalStateException("版本矩阵不支持目标平台: " + ctx.platform().id());
            }
            loaded = true;
            history.add("onLoad 校验通过: 平台 %s / 产物 %d 项（依赖顺序已解析）"
                    .formatted(ctx.platform(), ordered));
        } catch (RuntimeException ex) {
            loaded = false;
            history.add("onLoad 校验失败: " + ex.getMessage());
            throw ex;
        }
    }

    @Override
    public void onEnable(InstallContext ctx) {
        if (!loaded) {
            throw new IllegalStateException("onLoad 未通过校验，拒绝 onEnable");
        }
        history.add("onEnable 事件桥与探针注册完成: 平台 %s".formatted(ctx.platform()));
    }

    @Override
    public void onTick(TickContext ctx) {
        if (!loaded) {
            return;
        }
        if (tickRecords < TICK_HISTORY_LIMIT) {
            history.add("onTick #%d 采样: TPS 见指标流".formatted(ctx.tick()));
            tickRecords++;
        }
    }

    @Override
    public void onShutdown(InstallContext ctx) {
        history.add("onShutdown 证据收尾: 平台 %s".formatted(ctx.platform()));
        loaded = false;
    }

    /** 生命周期日志（验收证据）。 */
    public List<String> history() {
        return List.copyOf(history);
    }

    public boolean loaded() {
        return loaded;
    }

    public void clearHistory() {
        history.clear();
        tickRecords = 0;
    }
}
