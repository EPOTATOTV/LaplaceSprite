package io.laplace.sprite.render;

import io.laplace.sprite.core.spi.VersionOrder;

/**
 * 图形管线（升级实施文档 v2 L2-02）。
 *
 * <p>26.4 Snapshot 1 起 Vulkan 成为默认图形 API，OpenGL 退为兼容路径。两条管线的
 * 光栅化与 OIT 实现不同，像素 diff 容差必须分开给，不能再用一个数字兜底。</p>
 */
public enum RenderPipeline {

    OPENGL("opengl", 0.5),
    VULKAN("vulkan", 0.8);

    /** Vulkan 成为默认图形 API 的起始版本。 */
    private static final String VULKAN_DEFAULT_VERSION = "26.4";

    private final String id;
    private final double tolerancePercent;

    RenderPipeline(String id, double tolerancePercent) {
        this.id = id;
        this.tolerancePercent = tolerancePercent;
    }

    public String id() {
        return id;
    }

    /** 26.x 版本下该管线的像素容差。 */
    public double tolerancePercent() {
        return tolerancePercent;
    }

    /** 某 MC 版本是否默认走 Vulkan。 */
    public static boolean vulkanDefault(String mcVersion) {
        return mcVersion != null
                && VersionOrder.compare(mcVersion, VULKAN_DEFAULT_VERSION) >= 0;
    }
}
