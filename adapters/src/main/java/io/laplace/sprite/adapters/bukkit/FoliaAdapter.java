package io.laplace.sprite.adapters.bukkit;

import io.laplace.sprite.adapters.AbstractLaplaceAdapter;
import io.laplace.sprite.adapters.AdapterStatus;
import io.laplace.sprite.adapters.PlatformFacts;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.spi.SupportRange;

import java.util.List;

/**
 * Folia（14.2）：并发变体。同一套 Bukkit API，但调度模型换成四个区域级调度器。
 *
 * <p>跨区域访问不能直连，必须排队交由目标区域的调度器执行；这一点在 {@link #routeTask} 里显式建模。</p>
 */
public final class FoliaAdapter extends AbstractLaplaceAdapter {

    /** 四个调度器（14.2 Folia 行）。 */
    public static final List<String> SCHEDULERS = List.of(
            "GlobalRegionScheduler", "RegionScheduler", "EntityScheduler", "AsyncScheduler");

    private static final PlatformFacts FACTS = new PlatformFacts(
            "Folia",
            "构建 plugins/ 目录，paper-plugin.yml 必须声明 folia-supported",
            "paper-plugin.yml（folia-supported: true）",
            "GlobalRegionScheduler / RegionScheduler / EntityScheduler / AsyncScheduler",
            AdapterStatus.CONCURRENT_VARIANT,
            List.of("区域 tick 事件额外携带区域 id，区域并行下同一 tick 号可对应多个区域",
                    "跨区域访问必须排队到目标区域，直连访问在真实环境会抛异常",
                    "断言的时序要求按区域独立成立，不做跨区域同步假设",
                    "并发安全断言：多区域事件并发灌入不得丢事件"));

    public FoliaAdapter() {
        super("folia", PlatformFamily.FOLIA, SupportRange.minecraft("1.19.4", null), FACTS);
    }

    /** 任务类型（14.2 Folia 四种调度语义）。 */
    public enum TaskKind {
        GLOBAL,
        REGION,
        ENTITY,
        IO
    }

    /**
     * 路由决策。
     *
     * @param kind              任务类型
     * @param scheduler         目标调度器
     * @param crossRegionQueued 是否因跨区域而排队（而非直连执行）
     */
    public record RouteDecision(TaskKind kind, String scheduler, boolean crossRegionQueued) {
    }

    public List<String> schedulers() {
        return SCHEDULERS;
    }

    /** 无区域上下文时的路由（全局视角）。 */
    public RouteDecision routeTask(TaskKind kind) {
        return routeTask(kind, null, null);
    }

    /**
     * 带区域上下文的路由：REGION / ENTITY 任务若目标区域与当前区域不同，必须排队。
     */
    public RouteDecision routeTask(TaskKind kind, String currentRegion, String targetRegion) {
        String scheduler = switch (kind) {
            case GLOBAL -> "GlobalRegionScheduler";
            case REGION -> "RegionScheduler";
            case ENTITY -> "EntityScheduler";
            case IO -> "AsyncScheduler";
        };
        boolean crossRegion = kind == TaskKind.REGION || kind == TaskKind.ENTITY;
        boolean queued = crossRegion
                && currentRegion != null && targetRegion != null
                && !currentRegion.equals(targetRegion);
        return new RouteDecision(kind, scheduler, queued);
    }
}
