package io.laplace.sprite.adapters.matrix;

import com.fasterxml.jackson.databind.JsonNode;
import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.VersionMatrixEntry;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 离线版本矩阵快照读取（14.7）：{@code adapters/src/main/resources/version-matrix/*.json}。
 *
 * <p>不使用记录绑定，直接读 JSON 树——快照格式演进时不会把构建打断在反序列化上。</p>
 */
final class MatrixSnapshots {

    private static final String ROOT = "/version-matrix/";
    private static final Instant SNAPSHOT_TIME = Instant.parse("2026-01-01T00:00:00Z");

    private MatrixSnapshots() {
    }

    static List<VersionMatrixEntry> load(String fileName, PlatformFamily family, String sourceName) {
        JsonNode root = read(ROOT + fileName);
        if (root == null) {
            return List.of();
        }
        List<VersionMatrixEntry> entries = new ArrayList<>();
        for (JsonNode item : root.path("entries")) {
            String minecraft = text(item, "minecraftVersion");
            if (minecraft == null) {
                continue;
            }
            entries.add(new VersionMatrixEntry(family, minecraft, text(item, "loaderVersion"),
                    sourceName, SNAPSHOT_TIME, false));
        }
        return List.copyOf(entries);
    }

    static JsonNode read(String resource) {
        try (InputStream stream = MatrixSnapshots.class.getResourceAsStream(resource)) {
            if (stream == null) {
                return null;
            }
            return Json.jsonMapper().readTree(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new UncheckedIOException("读取版本矩阵快照失败: " + resource, ex);
        }
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }
}
