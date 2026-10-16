package io.laplace.sprite.crashdiag;

import com.fasterxml.jackson.databind.JsonNode;
import io.laplace.sprite.core.io.Json;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 类索引（15.5）：包名/类名前缀 → modId 的映射，用于把栈帧归因到具体 mod。
 *
 * <p>映射可从 {@code /crashdiag/class-index.json} 加载，也可由调用方注入。
 * 索引天生不全，所以归因结果必须带覆盖率，见 {@link FrameAttributor}。</p>
 */
public final class ClassIndex {

    /** 随模块附带的类索引资源。 */
    public static final String DEFAULT_RESOURCE = "/crashdiag/class-index.json";

    private final List<Entry> entries;

    private ClassIndex(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    public static ClassIndex of(List<Entry> entries) {
        return new ClassIndex(entries);
    }

    /** 空索引：一切帧都解析不出来，覆盖率必为 0。 */
    public static ClassIndex empty() {
        return new ClassIndex(List.of());
    }

    /** 加载随模块附带的索引。资源缺失时返回空索引，不抛异常。 */
    public static ClassIndex defaultIndex() {
        try (InputStream stream = ClassIndex.class.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                return empty();
            }
            return fromJson(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            return empty();
        }
    }

    /** 解析 {@code {"entries":[{"prefix":"...","modId":"..."}]}}。 */
    public static ClassIndex fromJson(String json) {
        List<Entry> parsed = new ArrayList<>();
        try {
            JsonNode root = Json.jsonMapper().readTree(json);
            for (JsonNode node : root.path("entries")) {
                String prefix = node.path("prefix").asText(null);
                String modId = node.path("modId").asText(null);
                if (prefix != null && modId != null && !prefix.isBlank()) {
                    parsed.add(new Entry(prefix, modId));
                }
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return new ClassIndex(parsed);
    }

    /** 前缀 → modId：取最长命中的前缀，前缀重叠时更具体的那条胜出。 */
    public Optional<String> modFor(String className) {
        if (className == null || className.isBlank()) {
            return Optional.empty();
        }
        String best = null;
        int bestLength = -1;
        for (Entry entry : entries) {
            if (className.startsWith(entry.prefix()) && entry.prefix().length() > bestLength) {
                best = entry.modId();
                bestLength = entry.prefix().length();
            }
        }
        return Optional.ofNullable(best);
    }

    public List<Entry> entries() {
        return entries;
    }

    public int size() {
        return entries.size();
    }

    /**
     * 一条前缀映射。
     *
     * @param prefix 包名或类名前缀，如 {@code net.fabricmc.} 或 {@code com.example.mymod.}
     * @param modId  归属的 mod / 插件 id
     */
    public record Entry(String prefix, String modId) {
    }
}
