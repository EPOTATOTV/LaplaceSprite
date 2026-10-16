package io.laplace.sprite.adapters.artifact;

import com.fasterxml.jackson.databind.JsonNode;
import io.laplace.sprite.adapters.support.HttpFetcher;
import io.laplace.sprite.adapters.support.OfflineSnapshots;
import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.core.model.Artifact;
import io.laplace.sprite.core.model.ArtifactRole;
import io.laplace.sprite.core.model.ArtifactSource;
import io.laplace.sprite.core.model.ArtifactSpec;
import io.laplace.sprite.core.spi.ArtifactResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CurseForge 客户端（14.5）：base URL 与 API key 可配置，网络不可用时退化到本地快照。
 *
 * <p>列表接口每页 50 条、总量上限 10000，见 {@link #PAGE_SIZE} / {@link #MAX_RESULTS}——
 * 分页越界要在调用方拦住，因为平台侧返回的是静默截断。</p>
 */
public final class CurseForgeClient implements ArtifactResolver {

    public static final String DEFAULT_BASE_URL = "https://api.curseforge.com/v1";
    public static final int PAGE_SIZE = 50;
    public static final int MAX_RESULTS = 10000;

    private final URI baseUrl;
    private final String apiKey;
    private final HttpFetcher http;

    public CurseForgeClient() {
        this(URI.create(DEFAULT_BASE_URL), null, true);
    }

    public CurseForgeClient(URI baseUrl, String apiKey, boolean networkEnabled) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.http = new HttpFetcher(Duration.ofSeconds(3), networkEnabled);
    }

    /** 分页数：按 50/页向上取整，并受 10000 条上限约束。 */
    public int pageCount(int totalResults) {
        if (totalResults <= 0) {
            return 0;
        }
        int capped = Math.min(totalResults, MAX_RESULTS);
        return (capped + PAGE_SIZE - 1) / PAGE_SIZE;
    }

    /** 可达的最大页码（0 起算）。 */
    public int lastPageIndex() {
        return pageCount(MAX_RESULTS) - 1;
    }

    @Override
    public Artifact resolve(ArtifactSpec spec) {
        String projectId = spec.coordinate();
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("CurseForge projectId 为空");
        }
        ArtifactRole role = spec.role() == null ? ArtifactRole.MOD : spec.role();
        Optional<Artifact> online = fetchProject(projectId, spec.version(), role);
        return online.or(() -> OfflineSnapshots.curseforge(projectId, spec.version()))
                .orElseGet(() -> deterministic(projectId, spec.version(), role));
    }

    @Override
    public List<Artifact> resolveDependencies(Artifact artifact) {
        return List.of();
    }

    private Optional<Artifact> fetchProject(String projectId, String version, ArtifactRole role) {
        Optional<String> body = http.get(URI.create(baseUrl + "/mods/" + projectId), headers());
        if (body.isEmpty()) {
            return Optional.empty();
        }
        JsonNode data = tree(body.get()).path("data");
        if (data.isMissingNode()) {
            return Optional.empty();
        }
        JsonNode file = pickFile(data.path("latestFiles"), version);
        String fileName = file.isMissingNode() ? null : text(file, "fileName");
        String resolvedVersion = fileName == null
                ? text(data, "name")
                : fileName.replaceAll("\\.jar$", "");
        if (resolvedVersion == null) {
            return Optional.empty();
        }
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("projectId", text(data, "id"));
        metadata.put("offline", "false");
        String location = file.isMissingNode() ? null : text(file, "downloadUrl");
        return Optional.of(new Artifact("curseforge:" + projectId, text(data, "name"), resolvedVersion,
                ArtifactSource.CURSEFORGE, location, null, role, List.of(), metadata));
    }

    private static JsonNode pickFile(JsonNode files, String version) {
        if (!files.isArray() || files.isEmpty()) {
            return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
        }
        if (version != null && !version.isBlank()) {
            for (JsonNode candidate : files) {
                String name = text(candidate, "fileName");
                if (name != null && name.contains(version)) {
                    return candidate;
                }
            }
        }
        return files.get(0);
    }

    private Map<String, String> headers() {
        return apiKey == null || apiKey.isBlank() ? Map.of() : Map.of("x-api-key", apiKey);
    }

    /** 无快照时的确定性合成。 */
    static Artifact deterministic(String projectId, String version, ArtifactRole role) {
        String resolved = version == null || version.isBlank() ? "0.0.0" : version;
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("offline", "true");
        metadata.put("synthesised", "true");
        metadata.put("projectId", projectId);
        return new Artifact("curseforge:" + projectId, projectId, resolved, ArtifactSource.CURSEFORGE,
                "https://edge.forgecdn.net/files/%s/%s.jar".formatted(projectId, resolved), null, role,
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
            throw new UncheckedIOException("解析 CurseForge 响应失败", ex);
        }
    }
}
