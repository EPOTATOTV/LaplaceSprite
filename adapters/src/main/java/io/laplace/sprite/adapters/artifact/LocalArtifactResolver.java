package io.laplace.sprite.adapters.artifact;

import io.laplace.sprite.core.model.Artifact;
import io.laplace.sprite.core.model.ArtifactRole;
import io.laplace.sprite.core.model.ArtifactSpec;
import io.laplace.sprite.core.spi.ArtifactResolver;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 本地产物（14.5）：{@code BUILT} / {@code LOCAL} 来源，坐标是文件路径。
 */
public final class LocalArtifactResolver implements ArtifactResolver {

    @Override
    public Artifact resolve(ArtifactSpec spec) {
        String coordinate = spec.coordinate();
        if (coordinate == null || coordinate.isBlank()) {
            throw new IllegalArgumentException("本地产物路径为空");
        }
        Path path = Path.of(coordinate);
        String fileName = path.getFileName() == null ? coordinate : path.getFileName().toString();
        String version = spec.version() == null || spec.version().isBlank() ? "local" : spec.version();
        ArtifactRole role = spec.role() == null ? ArtifactRole.LIBRARY : spec.role();
        return new Artifact("local:" + fileName, fileName, version, spec.source(), path.toAbsolutePath().toString(),
                null, role, List.of(), Map.of("local", "true"));
    }

    @Override
    public List<Artifact> resolveDependencies(Artifact artifact) {
        return List.of();
    }
}
