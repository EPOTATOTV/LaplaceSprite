package io.laplace.sprite.render;

import java.util.Objects;

/**
 * 渲染矩阵单元（15.12 P1）：MC 版本 × 渲染后端（可带光影包）。
 *
 * @param mcVersion        Minecraft 版本
 * @param backend          渲染后端
 * @param shaderPack       光影包名；不使用光影时为空
 * @param diffEnabled      是否参与截图像素 diff
 * @param tolerancePercent 像素容差
 */
public record RenderMatrixCell(String mcVersion, RenderBackend backend, String shaderPack,
                               boolean diffEnabled, double tolerancePercent) {

    public RenderMatrixCell {
        Objects.requireNonNull(mcVersion, "mcVersion");
        Objects.requireNonNull(backend, "backend");
        shaderPack = shaderPack == null || shaderPack.isBlank() ? null : shaderPack;
        if (tolerancePercent < 0 || tolerancePercent > 100) {
            throw new IllegalArgumentException("像素容差必须在 0–100 之间: " + tolerancePercent);
        }
        // 26.4 之前 VulkanMod 仍是实验性渲染器重写，只做启动 + 渲染一帧冒烟；
        // 26.4 起 Vulkan 默认化才允许进 diff。这里统一归一化，避免调用方误开门禁。
        diffEnabled = diffEnabled && backend.diffAllowed(mcVersion);
    }

    /** 是否只做冒烟（不比对像素）。 */
    public boolean smokeOnly() {
        return !diffEnabled;
    }
}
