package io.laplace.sprite.render;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * 渲染金标准样本库（升级实施文档 L-06，v2 L2-02 扩充 26.3/26.4）。
 *
 * <p>只登记场景与路径，不携带 PNG 本体：金标准截图由真实客户端在 CI 上生成后入库，
 * 代码侧负责保证 26.3 基线与 26.4 Vulkan 默认化（OIT 深度 bin）都有可引用的用例名。
 * 像素容差由 {@link RenderMatrix#toleranceFor(String, RenderBackend)} 按管线给出。</p>
 */
public final class RenderGoldenLibrary {

    /**
     * 一个渲染样本。
     *
     * @param id          用例 id，同时是 golden 文件名
     * @param mcVersion   目标 Minecraft 版本
     * @param description 覆盖的渲染内容
     */
    public record GoldenCase(String id, String mcVersion, String description) {

        public GoldenCase {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(mcVersion, "mcVersion");
            description = description == null ? "" : description;
        }
    }

    private static final List<GoldenCase> CASES = List.of(
            new GoldenCase("oit-depth-bins", "26.4",
                    "26.4 Snapshot 1：Vulkan 默认管线的深度 bin OIT 混合"),
            new GoldenCase("vulkan-terrain", "26.4",
                    "26.4 Snapshot 1：Vulkan 地形与光照基线，用于与 OpenGL 对照"),
            new GoldenCase("world-spawn", "26.3", "26.3 基线：主世界出生点，用于版本间对照"),
            new GoldenCase("sulfur-cavern", "26.2", "硫磺洞穴：新方块的光照、遮挡与粒子"),
            new GoldenCase("new-dimension", "26.2", "新维度：天空、雾效与区块边界"),
            new GoldenCase("new-effects", "26.2", "新效果：状态图标与屏幕后处理"),
            new GoldenCase("world-spawn", "26.1", "26.1 基线：主世界出生点，用于版本间对照"));

    public List<GoldenCase> cases() {
        return CASES;
    }

    public List<GoldenCase> forVersion(String mcVersion) {
        return CASES.stream().filter(golden -> golden.mcVersion().equals(mcVersion)).toList();
    }

    /**
     * 某样本在某后端下的 golden 路径。
     *
     * @throws IllegalArgumentException 该后端在目标版本不参与像素 diff（如 26.2 的 VulkanMod）
     */
    public Path goldenPng(GoldenPathResolver resolver, GoldenCase golden, RenderBackend backend) {
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(golden, "golden");
        Objects.requireNonNull(backend, "backend");
        if (!backend.diffAllowed(golden.mcVersion())) {
            throw new IllegalArgumentException(backend + " 在 " + golden.mcVersion()
                    + " 只做冒烟，不进入像素 diff 门禁");
        }
        RenderMatrixCell cell = new RenderMatrixCell(golden.mcVersion(), backend, null, true,
                RenderMatrix.toleranceFor(golden.mcVersion(), backend));
        return resolver.goldenPng(cell, golden.id());
    }
}
