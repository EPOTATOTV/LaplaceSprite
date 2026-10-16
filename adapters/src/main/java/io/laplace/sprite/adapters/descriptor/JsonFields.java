package io.laplace.sprite.adapters.descriptor;

import com.fasterxml.jackson.databind.JsonNode;
import io.laplace.sprite.core.io.Json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 描述符解析的读字段小工具：把「字段可能不存在 / 可能是单值也可能是列表」这类差异收敛掉。
 */
final class JsonFields {

    private JsonFields() {
    }

    static JsonNode json(String content) {
        try {
            return Json.jsonMapper().readTree(content);
        } catch (IOException ex) {
            throw new IllegalArgumentException("描述符不是合法 JSON: " + ex.getMessage(), ex);
        }
    }

    static JsonNode yaml(String content) {
        try {
            return Json.yamlMapper().readTree(content);
        } catch (IOException ex) {
            throw new IllegalArgumentException("描述符不是合法 YAML: " + ex.getMessage(), ex);
        }
    }

    /** 标量字段；容器或缺失都返回 null。 */
    static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.isContainerNode() ? null : value.asText();
    }

    /** 单值、列表都吃，统一成列表。 */
    static List<String> strings(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return List.of();
        }
        if (value.isArray()) {
            List<String> out = new ArrayList<>();
            value.forEach(item -> {
                if (item.isValueNode()) {
                    out.add(item.asText());
                }
            });
            return List.copyOf(out);
        }
        return List.of(value.asText());
    }

    static boolean bool(JsonNode node, String field, boolean fallback) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? fallback : value.asBoolean(fallback);
    }

    /** 原样保留字段值用于报告；缺失返回空串。 */
    static String raw(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }
}
