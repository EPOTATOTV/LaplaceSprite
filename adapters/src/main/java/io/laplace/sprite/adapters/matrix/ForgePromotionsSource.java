package io.laplace.sprite.adapters.matrix;

import com.fasterxml.jackson.databind.JsonNode;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.VersionMatrixEntry;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Forge promotions 来源（14.7）：{@code promotions_slim.json} 的 promos 键形如
 * {@code <minecraft>-latest}，值是加载器版本。
 */
public final class ForgePromotionsSource extends AbstractVersionMatrixSource {

    public static final URI DEFAULT_ENDPOINT =
            URI.create("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json");

    private static final String LATEST_SUFFIX = "-latest";

    private final PlatformFamily family = PlatformFamily.FORGE;
    private final URI endpoint;

    public ForgePromotionsSource(boolean networkEnabled, Path pinFile) {
        this(DEFAULT_ENDPOINT, networkEnabled, pinFile);
    }

    public ForgePromotionsSource(URI endpoint, boolean networkEnabled, Path pinFile) {
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
        return "forge.json";
    }

    @Override
    protected String sourceName() {
        return "forge-promotions";
    }

    @Override
    protected List<VersionMatrixEntry> parsePayload(String body) {
        JsonNode promos = FabricMetaSource.tree(body).path("promos");
        if (!promos.isObject()) {
            return List.of();
        }
        Instant fetchedAt = Instant.now();
        List<VersionMatrixEntry> entries = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = promos.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (!entry.getKey().endsWith(LATEST_SUFFIX)) {
                continue;
            }
            String minecraft = entry.getKey().substring(0, entry.getKey().length() - LATEST_SUFFIX.length());
            entries.add(new VersionMatrixEntry(family, minecraft, entry.getValue().asText(), sourceName(),
                    fetchedAt, false));
        }
        return List.copyOf(entries);
    }
}
