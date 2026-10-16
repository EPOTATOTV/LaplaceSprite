package io.laplace.sprite.adapters.matrix;

import com.fasterxml.jackson.databind.JsonNode;
import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.VersionMatrixEntry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Fabric Meta 来源（14.7）：{@code /v2/versions/loader} 给出加载器版本。
 *
 * <p>端点只返回加载器列表，Minecraft 侧取本地快照里的版本集合，交叉成矩阵条目。</p>
 */
public final class FabricMetaSource extends AbstractVersionMatrixSource {

    public static final URI DEFAULT_ENDPOINT = URI.create("https://meta.fabricmc.net/v2/versions/loader");

    private static final int MAX_LOADERS = 4;

    private final PlatformFamily family = PlatformFamily.FABRIC;
    private final URI endpoint;

    public FabricMetaSource(boolean networkEnabled, Path pinFile) {
        this(DEFAULT_ENDPOINT, networkEnabled, pinFile);
    }

    public FabricMetaSource(URI endpoint, boolean networkEnabled, Path pinFile) {
        super(networkEnabled, pinFile);
        this.endpoint = endpoint == null ? DEFAULT_ENDPOINT : endpoint;
    }

    @Override
    public PlatformFamily family() {
        return family;
    }

    @Override
    protected URI endpoint() {
        return endpoint;
    }

    @Override
    protected String snapshotFile() {
        return "fabric.json";
    }

    @Override
    protected String sourceName() {
        return "fabric-meta";
    }

    @Override
    protected List<VersionMatrixEntry> parsePayload(String body) {
        List<String> loaders = loaderVersions(body);
        if (loaders.isEmpty()) {
            return List.of();
        }
        List<String> minecraftVersions = snapshot(family).stream()
                .map(VersionMatrixEntry::minecraftVersion)
                .distinct()
                .toList();
        Instant fetchedAt = Instant.now();
        List<VersionMatrixEntry> entries = new ArrayList<>();
        for (String minecraft : minecraftVersions) {
            for (String loader : loaders) {
                entries.add(new VersionMatrixEntry(family, minecraft, loader, sourceName(), fetchedAt, false));
            }
        }
        return List.copyOf(entries);
    }

    /** 响应形如 {@code [{"version":"0.19.5","stable":true}, ...]}。 */
    static List<String> loaderVersions(String body) {
        JsonNode root = tree(body);
        if (!root.isArray()) {
            return List.of();
        }
        List<String> versions = new ArrayList<>();
        for (JsonNode item : root) {
            JsonNode version = item.path("version");
            if (version.isValueNode()) {
                versions.add(version.asText());
            }
            if (versions.size() >= MAX_LOADERS) {
                break;
            }
        }
        return versions;
    }

    static JsonNode tree(String body) {
        try {
            return Json.jsonMapper().readTree(body);
        } catch (IOException ex) {
            throw new UncheckedIOException("解析版本矩阵响应失败", ex);
        }
    }
}
