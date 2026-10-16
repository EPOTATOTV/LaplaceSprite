package io.laplace.sprite.hotreload;

import java.util.List;

/**
 * 发布前冷启动全生命周期轨（15.2）：干净容器 → 冷启动 → 冒烟。
 *
 * <p>结构改动、插件变更、Mixin 变更一律走这条轨；reload 不能当测试手段（见 {@link ReloadPolicy}）。</p>
 */
public final class ColdStartLifecycle {

    public static final String CLEAN_CONTAINER = "clean-container";

    public static final String COLD_START = "cold-start";

    public static final String SMOKE = "smoke";

    public static final List<String> SEQUENCE = List.of(CLEAN_CONTAINER, COLD_START, SMOKE);

    /**
     * 校验结论。
     *
     * @param accepted      是否接受这条计划
     * @param rejectedSteps 被拒的步骤
     * @param reason        拒绝原因
     */
    public record Validation(boolean accepted, List<String> rejectedSteps, String reason) {

        public Validation {
            rejectedSteps = rejectedSteps == null ? List.of() : List.copyOf(rejectedSteps);
            reason = reason == null ? "" : reason;
        }
    }

    private final ReloadPolicy reloadPolicy;

    public ColdStartLifecycle() {
        this(new ReloadPolicy());
    }

    public ColdStartLifecycle(ReloadPolicy reloadPolicy) {
        this.reloadPolicy = reloadPolicy == null ? new ReloadPolicy() : reloadPolicy;
    }

    public HotReloadTrack track() {
        return HotReloadTrack.COLD_LIFECYCLE;
    }

    public List<String> plan() {
        return SEQUENCE;
    }

    public Validation validate(List<String> steps) {
        if (steps == null || steps.isEmpty()) {
            return new Validation(false, List.of(), "冷启动轨步骤不能为空");
        }
        List<String> rejected = steps.stream()
                .filter(step -> reloadPolicy.rejectionReason(step).isPresent())
                .toList();
        if (!rejected.isEmpty()) {
            return new Validation(false, rejected, reloadPolicy.rejectionReason(rejected.get(0)).orElse(""));
        }
        if (!steps.equals(SEQUENCE)) {
            return new Validation(false, steps, "冷启动轨步骤顺序必须是 " + String.join(" → ", SEQUENCE));
        }
        return new Validation(true, List.of(), "");
    }
}
