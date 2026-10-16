package io.laplace.sprite.adapters.support;

import com.fasterxml.jackson.databind.JsonNode;
import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.core.model.Artifact;
import io.laplace.sprite.core.model.ArtifactDependency;
import io.laplace.sprite.core.model.ArtifactRole;
import io.laplace.sprite.core.model.ArtifactSource;
import io.laplace.sprite.core.model.DependencySemantics;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 产物离线快照（14.5）：网络不可达时用本地快照给出确定性解析结果，而不是抛异常。
 *
 * <p>快照放在 {@code adapters/src/main/resources/artifact-snapshots/} 下，按来源分子目录。
 * 没有对应快照时由调用方退化到确定性合成。</p>
 */
public final class OfflineSnapshots {

    private static final String ROOT = "/artifact-snapshots/";

    private OfflineSnapshots() {
    }

    public static Optional<Artifact> modrinth(String slug, String version) {
        return load(ROOT + "modrinth/" + slug + ".json", ArtifactSource.MODRINTH, slug, version, ArtifactRole.MOD);
    }

    public static Optional<Artifact> curseforge(String projectId, String version) {
        return load(ROOT + "curseforge/" + projectId + ".json", ArtifactSource.CURSEFORGE, projectId,
                version, ArtifactRole.MOD);
    }

    private static Optional<Artifact> load(String resource, ArtifactSource source, String coordinate,
                                           String version, ArtifactRole fallbackRole) {
        JsonNode node = read(resource);
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Optional.empty();
        }
        String resolved = version == null || version.isBlank()
                ? text(node, "version", "0.0.0")
                : version;
        ArtifactRole role = roleOf(text(node, "role", fallbackRole.name()));
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("offline", "true");
        metadata.put("snapshot", resource);
        // 快照里留一份项目标识，运行期补全闭包时不必依赖展示名。
        metadata.put("projectId", coordinate);
        return Optional.of(new Artifact(
                text(node, "id", coordinate),
                text(node, "name", coordinate),
                resolved,
                source,
                text(node, "location", coordinate),
                null,
                role,
                dependencies(node.path("dependencies")),
                metadata));
    }

    private static List<ArtifactDependency> dependencies(JsonNode array) {
        if (!array.isArray()) {
            return List.of();
        }
        List<ArtifactDependency> dependencies = new ArrayList<>();
        for (JsonNode item : array) {
            DependencySemantics semantics = switch (text(item, "semantics", "HARD")) {
                case "SOFT" -> DependencySemantics.SOFT;
                case "REVERSE" -> DependencySemantics.REVERSE;
                default -> DependencySemantics.HARD;
            };
            boolean optional = item.path("optional").asBoolean(false);
            dependencies.add(new ArtifactDependency(text(item, "id", "unknown"), semantics,
                    item.path("range").isMissingNode() ? null : item.path("range").asText(), optional));
        }
        return List.copyOf(dependencies);
    }

    private static ArtifactRole roleOf(String name) {
        for (ArtifactRole role : ArtifactRole.values()) {
            if (role.name().equalsIgnoreCase(name)) {
                return role;
            }
        }
        return ArtifactRole.MOD;
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? fallback : value.asText();
    }

    /** 读不到资源（含快照不存在）时返回 null，由调用方决定退化方式。 */
    static JsonNode read(String resource) {
        try (InputStream stream = OfflineSnapshots.class.getResourceAsStream(resource)) {
            if (stream == null) {
                return null;
            }
            String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return Json.jsonMapper().readTree(content);
        } catch (IOException ex) {
            throw new UncheckedIOException("读取离线快照失败: " + resource, ex);
        }
    }
}
