package io.laplace.sprite.game;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.core.spi.VersionOrder;
import io.laplace.sprite.datapack.PackFormatTable;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 官方版本 JSON 到 {@link GameVersionMetadata} 的解析器。
 *
 * <p>只给核实过的版本填 dataVersion / protocolVersion。26.4 在实现时还没有正式版，
 * 稳定版数值无法核实，因此留空而不是拿快照值顶替。</p>
 */
public final class GameMetadataResolver {

    private static final Pattern NUMERIC_PREFIX = Pattern.compile("^(\\d+(?:\\.\\d+)*)");

    private static final Map<String, VersionNumbers> VERSION_NUMBERS = Map.of(
            "1.21.1", new VersionNumbers(3955, 767),
            "1.21.11", new VersionNumbers(4671, 774),
            "26.1", new VersionNumbers(4786, 775),
            "26.2", new VersionNumbers(4903, 776),
            "26.3", new VersionNumbers(5023, 777));

    private final PackFormatTable packFormatTable;

    public GameMetadataResolver() {
        this(new PackFormatTable());
    }

    public GameMetadataResolver(PackFormatTable packFormatTable) {
        this.packFormatTable = packFormatTable;
    }

    public GameVersionMetadata resolve(String versionId, String versionJson) {
        if (versionJson == null || versionJson.isBlank()) {
            throw new IllegalArgumentException("版本 JSON 为空: " + versionId);
        }
        JsonNode root;
        try {
            root = Json.jsonMapper().readTree(versionJson);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("版本 JSON 解析失败（" + versionId + "）: "
                    + ex.getOriginalMessage(), ex);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("版本 JSON 顶层必须是对象: " + versionId);
        }
        return resolve(versionId, root);
    }

    private GameVersionMetadata resolve(String versionId, JsonNode root) {
        if (versionId == null || versionId.isBlank()) {
            throw new IllegalArgumentException("版本 id 不能为空");
        }
        String type = requiredText(root, "type", "$.type", versionId);
        Instant releaseTime = releaseTime(root, versionId);
        String mainClass = optionalText(root, "mainClass");
        String assets = optionalText(root, "assets");
        if (assets == null) {
            JsonNode assetIndex = root.get("assetIndex");
            if (assetIndex != null && assetIndex.isObject()) {
                assets = optionalText(assetIndex, "id");
            }
        }

        JsonNode downloads = root.get("downloads");
        GameVersionMetadata.DownloadInfo client = downloadInfo(downloads, "client", versionId);
        GameVersionMetadata.DownloadInfo server = downloadInfo(downloads, "server", versionId);

        VersionNumbers numbers = VERSION_NUMBERS.get(versionId);
        OptionalInt dataVersion = numbers == null
                ? OptionalInt.empty() : OptionalInt.of(numbers.dataVersion());
        OptionalInt protocolVersion = numbers == null
                ? OptionalInt.empty() : OptionalInt.of(numbers.protocolVersion());

        return new GameVersionMetadata(versionId, type, releaseTime, mainClass, assets,
                packFormatTable.formatFor(versionId), dataVersion, protocolVersion,
                renderApiFor(versionId), mappingsFor(versionId, null), client, server);
    }

    /** 26.4 及以后（含 26.4 快照）走 Vulkan，其余 OpenGL。 */
    public static RenderApi renderApiFor(String versionId) {
        String base = numericBase(versionId);
        if (base != null && VersionOrder.compare(base, "26.4") >= 0) {
            return RenderApi.VULKAN;
        }
        return RenderApi.OPENGL;
    }

    /**
     * 映射集：Forge / NeoForge 固定 Mojmap；Fabric 在 26.1+ 用 Mojmap，1.21.11 及以前用 Yarn。
     * 落在两条线之间的未知版本标为混淆。
     */
    public static MappingSet mappingsFor(String versionId, String loader) {
        String normalizedLoader = loader == null ? "" : loader.trim().toLowerCase(Locale.ROOT);
        if (normalizedLoader.equals("forge") || normalizedLoader.equals("neoforge")) {
            return MappingSet.MOJMAP;
        }
        String base = numericBase(versionId);
        if (base == null) {
            return MappingSet.OBFUSCATED;
        }
        if (VersionOrder.compare(base, "26.1") >= 0) {
            return MappingSet.MOJMAP;
        }
        if (VersionOrder.compare(base, "1.21.11") <= 0) {
            return MappingSet.YARN;
        }
        return MappingSet.OBFUSCATED;
    }

    /** 去掉快照 / 预发布后缀，只留可比较的发布号前缀。 */
    private static String numericBase(String versionId) {
        if (versionId == null) {
            return null;
        }
        Matcher matcher = NUMERIC_PREFIX.matcher(versionId.trim());
        return matcher.find() ? matcher.group(1) : null;
    }

    private static Instant releaseTime(JsonNode root, String versionId) {
        String text = requiredText(root, "releaseTime", "$.releaseTime", versionId);
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("版本 " + versionId + " 的 releaseTime 不是 ISO-8601: "
                    + text, ex);
        }
    }

    private static GameVersionMetadata.DownloadInfo downloadInfo(JsonNode downloads, String key,
                                                                 String versionId) {
        if (downloads == null || !downloads.isObject()) {
            return null;
        }
        JsonNode node = downloads.get(key);
        if (node == null || !node.isObject()) {
            return null;
        }
        String url = requiredText(node, "url", "$.downloads." + key + ".url", versionId);
        String sha1 = requiredText(node, "sha1", "$.downloads." + key + ".sha1", versionId);
        String sha256 = optionalText(node, "sha256");
        long size = node.path("size").asLong(-1);
        if (size < 0) {
            throw new IllegalArgumentException("版本 " + versionId + " 的 downloads." + key
                    + ".size 缺失或为负");
        }
        return new GameVersionMetadata.DownloadInfo(url, sha1, sha256, size);
    }

    private static String requiredText(JsonNode node, String field, String path, String versionId) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("版本 " + versionId + " 缺少字段 " + path);
        }
        return value.asText();
    }

    private static String optionalText(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value != null && value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }

    private record VersionNumbers(int dataVersion, int protocolVersion) {
    }
}
