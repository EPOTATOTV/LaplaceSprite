package io.laplace.sprite.adapters.artifact;

import com.fasterxml.jackson.databind.JsonNode;
import io.laplace.sprite.adapters.support.HttpFetcher;
import io.laplace.sprite.adapters.support.OfflineSnapshots;
import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.core.model.Artifact;
import io.laplace.sprite.core.model.ArtifactDependency;
import io.laplace.sprite.core.model.ArtifactRole;
import io.laplace.sprite.core.model.ArtifactSource;
import io.laplace.sprite.core.model.ArtifactSpec;
import io.laplace.sprite.core.model.DependencySemantics;
import io.laplace.sprite.core.spi.ArtifactResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Modrinth 客户端（14.5）：base URL 与 API key 可配置，网络不可用时退化到本地快照。
 *
 * <p>契约注记：Modrinth 的 Maven 镜像不解析传递依赖，所以运行期依赖闭包在 API 层补全——
 * 见 {@link #resolveDependencies}。</p>
 */
public final class ModrinthClient implements ArtifactResolver {

    public static final String DEFAULT_BASE_URL = "https://api.modrinth.com/v2";

    private final URI baseUrl;
    private final String apiKey;
    private final HttpFetcher http;

    public ModrinthClient() {
        this(URI.create(DEFAULT_BASE_URL), null, true);
    }

    public ModrinthClient(URI baseUrl) {
        this(baseUrl, null, true);
    }

    public ModrinthClient(URI baseUrl, String apiKey, boolean networkEnabled) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.http = new HttpFetcher(Duration.ofSeconds(3), networkEnabled);
    }

    @Override
    public Artifact resolve(ArtifactSpec spec) {
        String slug = spec.coordinate();
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("Modrinth slug 为空");
        }
        ArtifactRole role = spec.role() == null ? ArtifactRole.MOD : spec.role();
        Optional<Artifact> online = fetchVersion(slug, spec.version(), role);
        return online.or(() -> OfflineSnapshots.modrinth(slug, spec.version()))
                .orElseGet(() -> deterministic(slug, spec.version(), role));
    }

    /**
     * 运行期闭包补全：读项目的版本列表，取目标版本的 dependencies 字段。
     * 离线时退回快照里声明的依赖。
     */
    @Override
    public List<Artifact> resolveDependencies(Artifact artifact) {
        List<ArtifactDependency> declared = fetchDependencies(artifact)
                .orElseGet(() -> offlineDependencies(artifact));
        List<Artifact> dependencies = new ArrayList<>(declared.size());
        for (ArtifactDependency dependency : declared) {
            String id = dependency.id();
            DependencySemantics semantics = dependency.semantics();
            ArtifactRole role = semantics == DependencySemantics.REVERSE ? ArtifactRole.LIBRARY : ArtifactRole.MOD;
            dependencies.add(resolve(new ArtifactSpec(ArtifactSource.MODRINTH, id, null, role, Map.of())));
        }
        return List.copyOf(dependencies);
    }

    /** 离线兜底：按快照里记录的项目标识回查依赖声明。 */
    private static List<ArtifactDependency> offlineDependencies(Artifact artifact) {
        String slug = artifact.metadata().getOrDefault("projectId", artifact.name());
        return OfflineSnapshots.modrinth(slug, artifact.version())
                .map(Artifact::dependencies)
                .orElse(List.of());
    }

    private Optional<Artifact> fetchVersion(String slug, String version, ArtifactRole role) {
        Optional<String> body = http.get(versionsUri(slug), requestHeaders());
        if (body.isEmpty()) {
            return Optional.empty();
        }
        JsonNode array;
        try {
            array = tree(body.get());
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
        if (!array.isArray() || array.isEmpty()) {
            return Optional.empty();
        }
        JsonNode selected = array.get(0);
        if (version != null && !version.isBlank()) {
            for (JsonNode candidate : array) {
                if (version.equals(text(candidate, "version_number"))) {
                    selected = candidate;
                    break;
                }
            }
        }
        String versionNumber = text(selected, "version_number");
        if (versionNumber == null) {
            return Optional.empty();
        }
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("projectId", text(selected, "project_id"));
        metadata.put("versionId", text(selected, "id"));
        metadata.put("offline", "false");
        return Optional.of(new Artifact(slug, slug, versionNumber, ArtifactSource.MODRINTH,
                primaryFileUrl(selected, slug, versionNumber), null, role,
                dependenciesOf(selected), metadata));
    }

    private Optional<List<ArtifactDependency>> fetchDependencies(Artifact artifact) {
        String projectId = artifact.metadata().get("projectId");
        String slug = projectId == null ? artifact.name() : projectId;
        Optional<String> body = http.get(versionsUri(slug), requestHeaders());
        if (body.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(dependenciesOf(tree(body.get())));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    /** Bearer 头只在显式配置 API key 时发送；匿名请求保持无凭据。 */
    private Map<String, String> requestHeaders() {
        return apiKey == null || apiKey.isBlank() ? Map.of() : Map.of("Authorization", "Bearer " + apiKey);
    }

    /** 项目标识可能是带空格的展示名，路径段必须转义。 */
    private URI versionsUri(String identifier) {
        String encoded = URLEncoder.encode(identifier, StandardCharsets.UTF_8).replace("+", "%20");
        return URI.create(baseUrl + "/project/" + encoded + "/version");
    }

    private List<ArtifactDependency> dependenciesOf(JsonNode versionNode) {
        JsonNode array = versionNode.path("dependencies");
        if (!array.isArray()) {
            return List.of();
        }
        List<ArtifactDependency> dependencies = new ArrayList<>();
        for (JsonNode item : array) {
            String id = text(item, "project_id");
            if (id == null) {
                id = text(item, "version_id");
            }
            if (id == null) {
                continue;
            }
            String type = text(item, "dependency_type");
            DependencySemantics semantics = switch (type == null ? "required" : type) {
                case "optional" -> DependencySemantics.SOFT;
                case "incompatible" -> DependencySemantics.REVERSE;
                default -> DependencySemantics.HARD;
            };
            dependencies.add(new ArtifactDependency(id, semantics, null,
                    semantics == DependencySemantics.SOFT));
        }
        return List.copyOf(dependencies);
    }

    private static String primaryFileUrl(JsonNode versionNode, String slug, String version) {
        for (JsonNode file : versionNode.path("files")) {
            if (file.path("primary").asBoolean(false)) {
                return text(file, "url");
            }
        }
        for (JsonNode file : versionNode.path("files")) {
            return text(file, "url");
        }
        return "https://cdn.modrinth.com/data/%s/versions/%s.jar".formatted(slug, version);
    }

    /** 无快照时的确定性合成：同输入必然得到同一产物。 */
    static Artifact deterministic(String slug, String version, ArtifactRole role) {
        String resolved = version == null || version.isBlank() ? "0.0.0" : version;
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("offline", "true");
        metadata.put("synthesised", "true");
        return new Artifact("modrinth:" + slug, slug, resolved, ArtifactSource.MODRINTH,
                "https://cdn.modrinth.com/data/%s/%s.jar".formatted(slug, resolved), null, role,
                List.of(), metadata);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static JsonNode tree(String body) {
        try {
            return Json.jsonMapper().readTree(body);
        } catch (IOException ex) {
            throw new UncheckedIOException("解析 Modrinth 响应失败", ex);
        }
    }
}
