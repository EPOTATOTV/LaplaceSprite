package io.laplace.sprite.hotreload;

/**
 * 热重载双轨（15.2）。
 */
public enum HotReloadTrack {

    /** 开发期热更新轨：Debug 启动，方法体改动直接 HotSwap。 */
    DEV_HOT_LOOP("开发期热更新轨"),
    /** 发布前冷启动全生命周期轨：干净容器 → 冷启动 → 冒烟。 */
    COLD_LIFECYCLE("发布前冷启动全生命周期轨");

    private final String displayName;

    HotReloadTrack(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
