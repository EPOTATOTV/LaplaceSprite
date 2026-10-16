package io.laplace.sprite.adapters.matrix;

import com.fasterxml.jackson.databind.JsonNode;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.VersionMatrixEntry;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * NeoForge Maven 版本来源（14.7）：{@code /api/maven/versions/releases/.../neoforge}。
 *
 * <p>NeoForge 的加载器版本直接把 Minecraft 版本编进来（{@code 21.11.0} ↔ 1.21.11），
 * 所以这里按主次版本号反推 Minecraft 版本。</p>
 */
public final class NeoForgeProjectsSource extends AbstractVersionMatrixSource {

    public static final URI DEFAULT_ENDPOINT =
            URI.create("https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge");

    private static final int SEGMENTS = 3;

    private final PlatformFamily family = PlatformFamily.NEOFORGE;
    private final URI endpoint;

    public NeoForgeProjectsSource(boolean networkEnabled, Path pinFile) {
        this(DEFAULT_ENDPOINT, networkEnabled, pinFile);
    }

    public NeoForgeProjectsSource(URI endpoint, boolean networkEnabled, Path pinFile) {
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
        return "neoforge.json";
    }

    @Override
    protected String sourceName() {
        return "neoforge-projects";
    }

    @Override
    protected List<VersionMatrixEntry> parsePayload(String body) {
        JsonNode versions = FabricMetaSource.tree(body).path("versions");
        if (!versions.isArray()) {
            return List.of();
        }
        Instant fetchedAt = Instant.now();
        List<VersionMatrixEntry> entries = new ArrayList<>();
        for (JsonNode item : versions) {
            if (!item.isValueNode()) {
                continue;
            }
            String loader = item.asText();
            String minecraft = minecraftOf(loader);
            if (minecraft == null) {
                continue;
            }
            entries.add(new VersionMatrixEntry(family, minecraft, loader, sourceName(), fetchedAt, false));
        }
        return List.copyOf(entries);
    }

    /** {@code 21.11.0} → {@code 1.21.11}；版本段不足 3 段直接放弃。 */
    public static String minecraftOf(String loaderVersion) {
        String[] parts = loaderVersion.split("\\.");
        if (parts.length < SEGMENTS) {
            return null;
        }
        return "1.%s.%s".formatted(parts[0], parts[1]);
    }
}
