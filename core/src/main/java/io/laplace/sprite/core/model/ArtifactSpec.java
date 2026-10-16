package io.laplace.sprite.core.model;

import java.util.Map;

/**
 * 产物解析请求：Maven 坐标 / Modrinth / CurseForge / packwiz.toml（14.4 ①）。
 *
 * @param source     来源
 * @param coordinate 坐标（Maven GAV / Modrinth slug / CurseForge projectId / packwiz 文件路径）
 * @param version    版本；空表示取默认
 * @param role       目标角色，决定装配目录
 * @param attributes 源特定属性（如 {@code side=server}、{@code releaseType=beta}）
 */
public record ArtifactSpec(
        ArtifactSource source,
        String coordinate,
        String version,
        ArtifactRole role,
        Map<String, String> attributes) {

    public ArtifactSpec {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static ArtifactSpec maven(String gav, ArtifactRole role) {
        return new ArtifactSpec(ArtifactSource.MAVEN, gav, null, role, Map.of());
    }

    public static ArtifactSpec modrinth(String slug, String version, ArtifactRole role) {
        return new ArtifactSpec(ArtifactSource.MODRINTH, slug, version, role, Map.of());
    }

    public static ArtifactSpec curseforge(String projectId, String version, ArtifactRole role) {
        return new ArtifactSpec(ArtifactSource.CURSEFORGE, projectId, version, role, Map.of());
    }

    public static ArtifactSpec packwiz(String path, ArtifactRole role) {
        return new ArtifactSpec(ArtifactSource.PACKWIZ, path, null, role, Map.of());
    }
}
