package io.laplace.sprite.mixin;

import com.fasterxml.jackson.databind.JsonNode;
import io.laplace.sprite.core.io.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Mixin Watchdog JSON 解析（15.11 P1）。
 *
 * <p>离线路径不认识真实 Watchdog 版本，所以只约定最小形状：
 * {@code {"mixins":[{"modId":"...","targetClass":"...","mixins":["..."]}]}}，
 * 根节点也可以是同样的数组。真实运行时由 Watchdog 输出此形状，测试直接喂文本。</p>
 */
public final class MixinWatchdogParser {

    private MixinWatchdogParser() {
    }

    /** 解析归因表；没有条目时返回空列表。 */
    public static List<MixinAttribution> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        JsonNode root;
        try {
            root = Json.jsonMapper().readTree(json);
        } catch (IOException ex) {
            throw new UncheckedIOException("Mixin Watchdog 输出不是合法 JSON", ex);
        }
        JsonNode entries = entries(root);
        List<MixinAttribution> attributions = new ArrayList<>();
        for (JsonNode entry : entries) {
            String modId = firstText(entry, "modId", "mod");
            String targetClass = firstText(entry, "targetClass", "target", "class");
            if (modId == null || targetClass == null) {
                continue;
            }
            attributions.add(new MixinAttribution(modId, targetClass, mixins(entry)));
        }
        return List.copyOf(attributions);
    }

    /** 归因表 → 可直接给崩溃诊断做类反查的索引。 */
    public static MixinAttributionIndex index(List<MixinAttribution> attributions) {
        return MixinAttributionIndex.from(attributions);
    }

    private static JsonNode entries(JsonNode root) {
        if (root.isArray()) {
            return root;
        }
        for (String field : List.of("mixins", "attributions", "activeMixins")) {
            JsonNode node = root.path(field);
            if (node.isArray()) {
                return node;
            }
        }
        return Json.jsonMapper().createArrayNode();
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isTextual() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    private static List<String> mixins(JsonNode entry) {
        JsonNode node = entry.path("mixins");
        if (node.isArray()) {
            List<String> names = new ArrayList<>();
            node.forEach(item -> {
                if (item.isTextual() && !item.asText().isBlank()) {
                    names.add(item.asText());
                }
            });
            return List.copyOf(names);
        }
        if (node.isTextual() && !node.asText().isBlank()) {
            return List.of(node.asText());
        }
        String single = firstText(entry, "mixin", "mixinClass");
        return single == null ? List.of() : List.of(single);
    }
}
