package io.laplace.sprite.core.model;

/**
 * Live View 目标（11.8 一键查看）：管理员旁观视角或任意受测实例第一视角。
 */
public record LiveViewTarget(String instanceId, String simId, String networkCondition, PlatformKey platform,
                             boolean adminSpectator) {

    public static LiveViewTarget adminSpectator(String instanceId, PlatformKey platform) {
        return new LiveViewTarget(instanceId, "ADMIN", "local", platform, true);
    }

    public static LiveViewTarget player(String instanceId, String simId, String networkCondition,
                                        PlatformKey platform) {
        return new LiveViewTarget(instanceId, simId, networkCondition, platform, false);
    }
}
