package io.laplace.sprite.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 渲染后端组合矩阵（15.12 P1）。
 *
 * <p>维度顺序与文档一致：MC 版本在外层、渲染后端在内层，每个组合跑同一场景 Gametest。
 * 矩阵容差比 P0 的 0.5% 放宽到 1.0%：不同后端的光栅化/抗锯齿差异本来就大于同后端版本间差异。</p>
 */
public final class RenderMatrix {

    public static final List<String> DEFAULT_MC_VERSIONS = List.of("1.21.1", "1.20.6");

    public static final List<RenderBackend> DEFAULT_BACKENDS =
            List.of(RenderBackend.VANILLA_OPENGL, RenderBackend.SODIUM, RenderBackend.VULKANMOD);

    public static final double DEFAULT_TOLERANCE_PERCENT = 1.0;

    private final List<String> mcVersions;
    private final List<RenderBackend> backends;
    private final String sodiumShaderPack;

    public RenderMatrix(List<String> mcVersions, List<RenderBackend> backends, String sodiumShaderPack) {
        this.mcVersions = List.copyOf(Objects.requireNonNull(mcVersions, "mcVersions"));
        this.backends = List.copyOf(Objects.requireNonNull(backends, "backends"));
        this.sodiumShaderPack = sodiumShaderPack == null || sodiumShaderPack.isBlank()
                ? null
                : sodiumShaderPack;
    }

    /** 默认矩阵：两个 MC 档 × 三个后端，Sodium 格带内置低负载光影。 */
    public static RenderMatrix defaults() {
        return new RenderMatrix(DEFAULT_MC_VERSIONS, DEFAULT_BACKENDS, ShaderPackSpec.LOW_LOAD.name());
    }

    public List<RenderMatrixCell> cells() {
        List<RenderMatrixCell> cells = new ArrayList<>(size());
        for (String mcVersion : mcVersions) {
            for (RenderBackend backend : backends) {
                String shaderPack = backend == RenderBackend.SODIUM ? sodiumShaderPack : null;
                cells.add(new RenderMatrixCell(mcVersion, backend, shaderPack, backend.diffAllowed(),
                        DEFAULT_TOLERANCE_PERCENT));
            }
        }
        return List.copyOf(cells);
    }

    public int size() {
        return mcVersions.size() * backends.size();
    }

    public List<String> mcVersions() {
        return mcVersions;
    }

    public List<RenderBackend> backends() {
        return backends;
    }
}
