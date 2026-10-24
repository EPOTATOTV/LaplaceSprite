package io.laplace.sprite.game;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.laplace.sprite.core.io.Json;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 官方版本清单（{@code version_manifest_v2.json}）。
 *
 * @param latestRelease  最新正式版 id
 * @param latestSnapshot 最新快照 id
 * @param versions       全部版本，顺序与官方清单一致
 */
public record VersionManifest(String latestRelease, String latestSnapshot, List<GameVersion> versions) {

    public VersionManifest {
        Objects.requireNonNull(latestRelease, "latestRelease");
        Objects.requireNonNull(latestSnapshot, "latestSnapshot");
        versions = List.copyOf(versions);
    }

    public Optional<GameVersion> find(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return versions.stream().filter(version -> version.id().equals(id)).findFirst();
    }

    public int count() {
        return versions.size();
    }

    public List<GameVersion> byType(String type) {
        if (type == null) {
            return List.of();
        }
        return versions.stream().filter(version -> version.type().equals(type)).toList();
    }

    /**
     * 解析官方清单。任何结构或字段问题都抛 {@link IllegalArgumentException}，消息里带字段路径与下标。
     */
    public static VersionManifest parse(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("版本清单为空");
        }
        JsonNode root;
        try {
            root = Json.jsonMapper().readTree(content);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("版本清单 JSON 解析失败: " + ex.getOriginalMessage(), ex);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("版本清单顶层必须是 JSON 对象");
        }
        JsonNode latest = root.get("latest");
        if (latest == null || !latest.isObject()) {
            throw new IllegalArgumentException("版本清单缺少 latest 对象");
        }
        String latestRelease = requiredText(latest, "release", "$.latest.release");
        String latestSnapshot = requiredText(latest, "snapshot", "$.latest.snapshot");

        JsonNode nodes = root.get("versions");
        if (nodes == null || !nodes.isArray()) {
            throw new IllegalArgumentException("版本清单缺少 versions 数组");
        }
        List<GameVersion> versions = new ArrayList<>(nodes.size());
        for (int index = 0; index < nodes.size(); index++) {
            versions.add(parseVersion(nodes.get(index), index));
        }
        if (versions.isEmpty()) {
            throw new IllegalArgumentException("版本清单 versions 为空");
        }
        return new VersionManifest(latestRelease, latestSnapshot, versions);
    }

    private static GameVersion parseVersion(JsonNode node, int index) {
        String path = "$.versions[" + index + "]";
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException(path + " 必须是 JSON 对象");
        }
        String id = requiredText(node, "id", path + ".id");
        String type = requiredText(node, "type", path + ".type");
        String url = requiredText(node, "url", path + ".url");
        String sha1 = requiredText(node, "sha1", path + ".sha1");
        String releaseTimeText = requiredText(node, "releaseTime", path + ".releaseTime");
        Instant releaseTime;
        try {
            releaseTime = Instant.parse(releaseTimeText);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(path + ".releaseTime 不是 ISO-8601 时间: "
                    + releaseTimeText, ex);
        }
        int complianceLevel = node.path("complianceLevel").asInt(0);
        return new GameVersion(id, type, url, sha1, releaseTime, complianceLevel,
                javaVersion(node.get("javaVersion"), path));
    }

    private static int javaVersion(JsonNode node, String path) {
        if (node == null || node.isNull()) {
            return 0;
        }
        if (node.isInt()) {
            return node.asInt();
        }
        if (node.isObject()) {
            JsonNode major = node.get("majorVersion");
            if (major != null && major.isInt()) {
                return major.asInt();
            }
        }
        throw new IllegalArgumentException(path + ".javaVersion 结构无法识别");
    }

    private static String requiredText(JsonNode node, String field, String path) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("缺少或为空的字段 " + path);
        }
        return value.asText();
    }
}
