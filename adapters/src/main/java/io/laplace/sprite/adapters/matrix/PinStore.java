package io.laplace.sprite.adapters.matrix;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.VersionMatrixEntry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * pin 持久化（14.7）：手动 pin 的矩阵条目存成 JSON，重建注册表时可直接读回。
 *
 * <p>没有配置文件路径时就只存在内存里，用于测试与临时会话。</p>
 */
public final class PinStore {

    private final Path file;
    private final List<VersionMatrixEntry> entries = new ArrayList<>();

    public PinStore(Path file) {
        this.file = file;
        if (file != null && Files.isRegularFile(file)) {
            entries.addAll(read(file));
        }
    }

    public Path file() {
        return file;
    }

    public List<VersionMatrixEntry> entries() {
        return List.copyOf(entries);
    }

    public void add(VersionMatrixEntry entry) {
        entries.add(entry);
        write();
    }

    private static List<VersionMatrixEntry> read(Path file) {
        List<VersionMatrixEntry> loaded = new ArrayList<>();
        try {
            JsonNode root = Json.jsonMapper().readTree(Files.readString(file));
            if (!root.isArray()) {
                return loaded;
            }
            for (JsonNode item : root) {
                PlatformFamily family = PlatformFamily.valueOf(item.path("family").asText());
                loaded.add(new VersionMatrixEntry(family, item.path("minecraftVersion").asText(),
                        item.path("loaderVersion").isNull() ? null : item.path("loaderVersion").asText(),
                        item.path("source").asText("manual"),
                        Instant.parse(item.path("fetchedAt").asText()),
                        item.path("pinned").asBoolean(true)));
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("读取 pin 文件失败: " + file, ex);
        }
        return loaded;
    }

    private void write() {
        if (file == null) {
            return;
        }
        ArrayNode array = Json.jsonMapper().createArrayNode();
        for (VersionMatrixEntry entry : entries) {
            ObjectNode node = array.addObject();
            node.put("family", entry.family().name());
            node.put("minecraftVersion", entry.minecraftVersion());
            if (entry.loaderVersion() == null) {
                node.putNull("loaderVersion");
            } else {
                node.put("loaderVersion", entry.loaderVersion());
            }
            node.put("source", entry.source());
            node.put("fetchedAt", entry.fetchedAt().toString());
            node.put("pinned", entry.pinned());
        }
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Json.jsonMapper().writerWithDefaultPrettyPrinter().writeValue(file.toFile(), array);
        } catch (IOException ex) {
            throw new UncheckedIOException("写入 pin 文件失败: " + file, ex);
        }
    }
}
