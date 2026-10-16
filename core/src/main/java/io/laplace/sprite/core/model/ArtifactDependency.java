package io.laplace.sprite.core.model;

import java.util.List;

/**
 * 单个依赖声明。
 *
 * @param id        依赖产物 id
 * @param semantics 硬/软/反向
 * @param range     版本区间（如 {@code [1.19,)}）；空表示不限
 * @param optional  是否可选（Velocity {@code @Dependency(optional)} / Allay optional）
 */
public record ArtifactDependency(String id, DependencySemantics semantics, String range, boolean optional) {

    public static ArtifactDependency hard(String id) {
        return new ArtifactDependency(id, DependencySemantics.HARD, null, false);
    }

    public static ArtifactDependency soft(String id) {
        return new ArtifactDependency(id, DependencySemantics.SOFT, null, true);
    }

    public static ArtifactDependency reverse(String id) {
        return new ArtifactDependency(id, DependencySemantics.REVERSE, null, false);
    }

    public static List<ArtifactDependency> none() {
        return List.of();
    }
}
