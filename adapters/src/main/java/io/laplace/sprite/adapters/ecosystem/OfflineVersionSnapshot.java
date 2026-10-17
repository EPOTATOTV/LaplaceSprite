package io.laplace.sprite.adapters.ecosystem;

import com.fasterxml.jackson.databind.JsonNode;
import io.laplace.sprite.core.io.Json;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 离线版本快照（14.9）：{@code adapters/src/main/resources/ecosystem/version-snapshot.json}。
 *
 * <p>同一份文件也被 {@code ci/generate-matrix.py} 读取，保证 Java 生成器与 CI 脚本的
 * 剪枝结果一致。快照是唯一默认来源，构造后不再访问网络。</p>
 */
public final class OfflineVersionSnapshot implements VersionSource {

    private static final String RESOURCE = "/ecosystem/version-snapshot.json";
    private static final String STABLE = "STABLE";
    private static final String LOADER_NEOFORGE = "neoforge";
    private static final String LOADER_FORGE = "forge";
    private static final String LOADER_FABRIC = "fabric";

    private final List<MinecraftRelease> minecraft;
    private final Map<String, List<LoaderRelease>> loaders;
    private final Map<String, PaperBuild> paper;
    private final Set<String> builds;

    private OfflineVersionSnapshot(List<MinecraftRelease> minecraft,
                                   Map<String, List<LoaderRelease>> loaders,
                                   Map<String, PaperBuild> paper,
                                   Set<String> builds) {
        this.minecraft = List.copyOf(minecraft);
        this.loaders = Map.copyOf(loaders);
        this.paper = Map.copyOf(paper);
        this.builds = Set.copyOf(builds);
    }

    public static OfflineVersionSnapshot loadDefault() {
        return fromJson(readResource());
    }

    public static OfflineVersionSnapshot fromJson(String json) {
        try {
            return parse(Json.jsonMapper().readTree(json));
        } catch (IOException ex) {
            throw new UncheckedIOException("解析离线版本快照失败", ex);
        }
    }

    private static OfflineVersionSnapshot parse(JsonNode root) {
        List<MinecraftRelease> minecraft = new ArrayList<>();
        for (JsonNode item : root.path("minecraft")) {
            minecraft.add(new MinecraftRelease(
                    text(item, "version"),
                    text(item, "channel"),
                    item.path("lts").asBoolean(false),
                    item.path("java").asInt(21)));
        }

        Map<String, List<LoaderRelease>> loaders = new LinkedHashMap<>();
        loaders.put(LOADER_FABRIC, releases(root.path("fabric"), LOADER_FABRIC));
        loaders.put(LOADER_NEOFORGE, releases(root.path("neoforge"), LOADER_NEOFORGE));
        loaders.put(LOADER_FORGE, releases(root.path("forge"), LOADER_FORGE));

        Map<String, PaperBuild> paper = new LinkedHashMap<>();
        for (JsonNode item : root.path("paper")) {
            String channel = text(item, "channel");
            if (!STABLE.equalsIgnoreCase(channel)) {
                continue;
            }
            String minecraftVersion = text(item, "minecraft");
            paper.put(minecraftVersion, new PaperBuild(minecraftVersion, text(item, "build"),
                    channel, item.path("java").asInt(21)));
        }

        Set<String> builds = new HashSet<>();
        for (JsonNode item : root.path("builds")) {
            builds.add(buildKey(text(item, "project"), text(item, "minecraft"), text(item, "loader")));
        }
        return new OfflineVersionSnapshot(minecraft, loaders, paper, builds);
    }

    private static List<LoaderRelease> releases(JsonNode array, String loader) {
        List<LoaderRelease> releases = new ArrayList<>();
        for (JsonNode item : array) {
            releases.add(new LoaderRelease(text(item, "minecraft"), loader,
                    text(item, "loaderVersion"), item.path("stable").asBoolean(true)));
        }
        return List.copyOf(releases);
    }

    @Override
    public List<MinecraftRelease> minecraftReleases() {
        return minecraft;
    }

    @Override
    public List<LoaderRelease> loaderReleases(String loader) {
        return loaders.getOrDefault(loader.toLowerCase(Locale.ROOT), List.of());
    }

    @Override
    public Optional<PaperBuild> paperBuild(String minecraft) {
        return Optional.ofNullable(paper.get(minecraft));
    }

    @Override
    public boolean hasBuild(String project, String minecraft, String loader) {
        return builds.contains(buildKey(project, minecraft, loader));
    }

    private static String buildKey(String project, String minecraft, String loader) {
        return "%s|%s|%s".formatted(project, minecraft, loader);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText();
    }

    private static String readResource() {
        try (InputStream stream = OfflineVersionSnapshot.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("缺少离线版本快照: " + RESOURCE);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("读取离线版本快照失败: " + RESOURCE, ex);
        }
    }
}
