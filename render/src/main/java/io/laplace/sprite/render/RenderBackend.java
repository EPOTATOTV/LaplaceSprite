package io.laplace.sprite.render;

/**
 * 渲染后端（15.12 P1，26.4 起扩展为管线分档）。
 *
 * <p>{@link #VULKANMOD} 是原生 Vulkan 的渲染器完整重写（不是 Zink 兼容层）。26.4 之前官方
 * 标注实验性，只做「启动 + 渲染一帧」冒烟；26.4 起 Vulkan 成为默认图形 API，纳入视觉 diff，
 * 但容差按 {@link RenderPipeline#VULKAN} 单独给。</p>
 */
public enum RenderBackend {

    VANILLA_OPENGL("vanilla-opengl", RenderPipeline.OPENGL),
    /** Sodium 重写渲染管线；光影冒烟格同时加载 Iris。 */
    SODIUM("sodium", RenderPipeline.OPENGL),
    VULKANMOD("vulkanmod", RenderPipeline.VULKAN);

    private final String id;
    private final RenderPipeline pipeline;

    RenderBackend(String id, RenderPipeline pipeline) {
        this.id = id;
        this.pipeline = pipeline;
    }

    /** golden 目录名。 */
    public String id() {
        return id;
    }

    public RenderPipeline pipeline() {
        return pipeline;
    }

    public boolean experimental() {
        return this == VULKANMOD;
    }

    /**
     * 该后端在指定 MC 版本下是否参与截图像素 diff。
     *
     * <p>VulkanMod 只在 Vulkan 默认化的版本（26.4 起）进 diff，更早的版本仍按实验性冒烟处理。</p>
     */
    public boolean diffAllowed(String mcVersion) {
        return this != VULKANMOD || RenderPipeline.vulkanDefault(mcVersion);
    }
}
