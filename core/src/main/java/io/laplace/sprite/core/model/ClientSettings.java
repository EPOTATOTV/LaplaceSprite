package io.laplace.sprite.core.model;

/**
 * 受测客户端设置（11.5 模组菜单）。
 *
 * @param behavior          行为切换
 * @param clickDistribution 点击分布（1-12 CPS / 蝴蝶 / 拖拽）
 * @param targetSwitch      目标切换
 * @param preInject         作弊样本预注入
 * @param autoRecord        证据自动录制
 * @param networkModelRef   网络条件
 */
public record ClientSettings(BehaviorProfile behavior, String clickDistribution, boolean targetSwitch,
                             boolean preInject, boolean autoRecord, String networkModelRef) {

    public static ClientSettings defaults() {
        return new ClientSettings(BehaviorProfile.SPRINT, "uniform-1-12", false, false, true, "default");
    }

    public ClientSettings withBehavior(BehaviorProfile newBehavior) {
        return new ClientSettings(newBehavior, clickDistribution, targetSwitch, preInject, autoRecord,
                networkModelRef);
    }
}
