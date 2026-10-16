package io.laplace.sprite.adapters.artifact;

import io.laplace.sprite.core.model.Artifact;
import io.laplace.sprite.core.model.ArtifactRole;
import io.laplace.sprite.core.model.ArtifactSource;
import io.laplace.sprite.core.model.ArtifactSpec;
import io.laplace.sprite.core.spi.ArtifactResolver;

import java.util.List;
import java.util.Map;

/**
 * Maven 坐标解析（14.5）：{@code group:artifact:version}。
 *
 * <p>Maven 侧不做传递依赖解析，闭包需要调用方补齐——Modrinth 的 Maven 镜像同样有这个限制，
 * 所以闭包统一在 API 层（{@link ModrinthClient#resolveDependencies}）完成。</p>
 */
public final class MavenArtifactResolver implements ArtifactResolver {

    private static final int MIN_PARTS = 3;

    @Override
    public Artifact resolve(ArtifactSpec spec) {
        String coordinate = spec.coordinate();
        if (coordinate == null || coordinate.isBlank()) {
            throw new IllegalArgumentException("Maven 坐标为空");
        }
        String[] parts = coordinate.split(":");
        if (parts.length < MIN_PARTS) {
            throw new IllegalArgumentException(
                    "Maven 坐标需形如 group:artifact:version，实际为「%s」".formatted(coordinate));
        }
        String group = parts[0];
        String name = parts[1];
        String version = spec.version() == null || spec.version().isBlank() ? parts[2] : spec.version();
        ArtifactRole role = spec.role() == null ? ArtifactRole.LIBRARY : spec.role();
        return new Artifact(group + ":" + name, name, version, ArtifactSource.MAVEN,
                "%s:%s:%s".formatted(group, name, version), null, role, List.of(),
                Map.of("group", group, "maven", "true"));
    }

    @Override
    public List<Artifact> resolveDependencies(Artifact artifact) {
        return List.of();
    }
}
