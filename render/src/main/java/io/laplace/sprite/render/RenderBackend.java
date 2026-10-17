package io.laplace.sprite.render;

/**
 * 渲染后端（15.12 P1）。
 *
 * <p>{@link #VULKANMOD} 是原生 Vulkan 的渲染器完整重写（不是 Zink 兼容层），官方标注实验性，
 * 因此只做「启动 + 渲染一帧」冒烟，不进入视觉 diff 门禁。</p>
 */
public enum RenderBackend {

    VANILLA_OPENGL("vanilla-opengl"),
    /** Sodium 重写渲染管线；光影冒烟格同时加载 Iris。 */
    SODIUM("sodium"),
    VULKANMOD("vulkanmod");

    private final String id;

    RenderBackend(String id) {
        this.id = id;
    }

    /** golden 目录名。 */
    public String id() {
        return id;
    }

    public boolean experimental() {
        return this == VULKANMOD;
    }

    /** 是否允许参与截图像素 diff。 */
    public boolean diffAllowed() {
        return this != VULKANMOD;
    }
}
