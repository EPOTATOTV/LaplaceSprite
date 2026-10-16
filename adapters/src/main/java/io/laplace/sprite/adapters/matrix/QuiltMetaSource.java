package io.laplace.sprite.adapters.matrix;

import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.VersionMatrixEntry;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Quilt Meta 来源（14.7）：{@code /v3/versions/loader}，响应形状与 Fabric 一致。
 */
public final class QuiltMetaSource extends AbstractVersionMatrixSource {

    public static final URI DEFAULT_ENDPOINT = URI.create("https://meta.quiltmc.org/v3/versions/loader");

    private final PlatformFamily family = PlatformFamily.QUILT;
    private final URI endpoint;

    public QuiltMetaSource(boolean networkEnabled, Path pinFile) {
        this(DEFAULT_ENDPOINT, networkEnabled, pinFile);
    }

    public QuiltMetaSource(URI endpoint, boolean networkEnabled, Path pinFile) {
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
        return "quilt.json";
    }

    @Override
    protected String sourceName() {
        return "quilt-meta";
    }

    @Override
    protected List<VersionMatrixEntry> parsePayload(String body) {
        List<String> loaders = FabricMetaSource.loaderVersions(body);
        if (loaders.isEmpty()) {
            return List.of();
        }
        Instant fetchedAt = Instant.now();
        List<VersionMatrixEntry> entries = new ArrayList<>();
        for (VersionMatrixEntry snapshotEntry : snapshot(family)) {
            for (String loader : loaders) {
                entries.add(new VersionMatrixEntry(family, snapshotEntry.minecraftVersion(), loader,
                        sourceName(), fetchedAt, false));
            }
        }
        return List.copyOf(entries);
    }
}
