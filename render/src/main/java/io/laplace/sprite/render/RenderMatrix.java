package io.laplace.sprite.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 渲染后端组合矩阵（15.12 P1）。
 *
 * <p>维度顺序与文档一致：MC 版本在外层、渲染后端在内层，每个组合跑同一场景 Gametest。
 * 26.2 起按升级实施文档改用 0.5% 的 P0 容差；旧版本仍保留 1.0%，因为不同后端的
 * 光栅化/抗锯齿差异本来就大于同后端版本间差异。</p>
 */
public final class RenderMatrix {

    public static final List<String> DEFAULT_MC_VERSIONS = List.of("26.2", "26.1", "1.21.1");

    public static final List<RenderBackend> DEFAULT_BACKENDS =
            List.of(RenderBackend.VANILLA_OPENGL, RenderBackend.SODIUM, RenderBackend.VULKANMOD);

    /** 26.2 渲染样本的像素容差（升级实施文档 L-06）。 */
    public static final double DEFAULT_TOLERANCE_PERCENT = 0.5;

    /** 26.x 之前的旧版本沿用放宽后的容差。 */
    public static final double LEGACY_TOLERANCE_PERCENT = 1.0;

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

    /** 默认矩阵：三个 MC 档 × 三个后端，Sodium 格带内置低负载光影。 */
    public static RenderMatrix defaults() {
        return new RenderMatrix(DEFAULT_MC_VERSIONS, DEFAULT_BACKENDS, ShaderPackSpec.LOW_LOAD.name());
    }

    /** 26.x 用 0.5% 容差，更早的版本沿用 1.0%。 */
    public static double toleranceFor(String mcVersion) {
        return mcVersion != null && mcVersion.startsWith("26.") ? DEFAULT_TOLERANCE_PERCENT
                : LEGACY_TOLERANCE_PERCENT;
    }

    public List<RenderMatrixCell> cells() {
        List<RenderMatrixCell> cells = new ArrayList<>(size());
        for (String mcVersion : mcVersions) {
            for (RenderBackend backend : backends) {
                String shaderPack = backend == RenderBackend.SODIUM ? sodiumShaderPack : null;
                cells.add(new RenderMatrixCell(mcVersion, backend, shaderPack, backend.diffAllowed(),
                        toleranceFor(mcVersion)));
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
