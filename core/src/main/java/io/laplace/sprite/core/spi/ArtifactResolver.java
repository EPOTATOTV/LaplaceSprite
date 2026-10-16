package io.laplace.sprite.core.spi;

import io.laplace.sprite.core.model.Artifact;
import io.laplace.sprite.core.model.ArtifactSpec;

import java.util.List;

/**
 * ① 产物解析（14.4）：Maven 坐标 / Modrinth / CurseForge / packwiz.toml。
 */
public interface ArtifactResolver {

    /** 解析单个产物坐标。 */
    Artifact resolve(ArtifactSpec spec);

    /** 解析依赖闭包。 */
    List<Artifact> resolveDependencies(Artifact artifact);
}
