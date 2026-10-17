package io.laplace.sprite.datapack;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.laplace.sprite.core.io.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * pack.mcmeta 解析器（15.8）：成功给 {@link PackMeta}，失败给带 JSON 路径与 Mojira 编号的
 * {@link PackMetaError}。
 *
 * <p>三条 Mojira 特征在这里固化：</p>
 * <ul>
 *   <li>{@code MC-123460}：pack.mcmeta 的 JSON 语法错误，原版在 {@code /reload} 时抛
 *       JsonSyntaxException / MalformedJsonException；出错路径从报错文本或跟踪解析求出。</li>
 *   <li>{@code MC-123459}：找不到 pack.mcmeta，日志里是 {@code Couldn't find pack meta}。</li>
 *   <li>{@code MC-310240}：含无法解析 JSON 的数据包导致世界加载失败。</li>
 * </ul>
 */
public final class PackMetaParser {

    public static final String FILE_NAME = "pack.mcmeta";

    public static final String MOJIRA_JSON_SYNTAX = "MC-123460";
    public static final String MOJIRA_MISSING_META = "MC-123459";
    public static final String MOJIRA_WORLD_LOAD = "MC-310240";

    private static final ObjectMapper MAPPER = Json.jsonMapper();
    private static final Pattern JSON_PATH = Pattern.compile("\\$(?:\\.[A-Za-z0-9_]+|\\[[0-9]+])*");
    private static final Pattern PACK_META_TOKEN = Pattern.compile("([\\w./\\\\:-]*pack\\.mcmeta)");
    private static final Pattern PACK_META_DIR = Pattern.compile("pack meta in\\s+([^\\s,;)]+)");
    private static final String[] TRIM_CHARS = {"\"", "'", "<", ">", ",", ";", "(", ")", "["};

    /** 解析 pack 根目录下的 pack.mcmeta。 */
    public PackMetaResult parsePack(Path packRoot) {
        if (packRoot == null) {
            throw new IllegalArgumentException("pack 根目录不能为空");
        }
        return parseFile(packRoot.resolve(FILE_NAME));
    }

    /** 解析指定文件；文件不存在按 MC-123459 处理。 */
    public PackMetaResult parseFile(Path packMeta) {
        if (packMeta == null || !Files.isRegularFile(packMeta)) {
            String file = packMeta == null ? FILE_NAME : packMeta.toString();
            return PackMetaResult.failure(new PackMetaError(file, "$.pack", PackMetaErrorKind.MISSING_META,
                    "Couldn't find pack meta in " + file, MOJIRA_MISSING_META));
        }
        try {
            return parse(packMeta.toString(), Files.readString(packMeta, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            return PackMetaResult.failure(new PackMetaError(packMeta.toString(), "$",
                    PackMetaErrorKind.UNREADABLE, "读取 pack.mcmeta 失败: " + ex.getMessage(), null));
        }
    }

    /** 解析文件内容；{@code file} 只用于报错定位。 */
    public PackMetaResult parse(String file, String content) {
        if (content == null || content.isBlank()) {
            return PackMetaResult.failure(new PackMetaError(file, "$", PackMetaErrorKind.JSON_SYNTAX,
                    "pack.mcmeta 为空", MOJIRA_JSON_SYNTAX));
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(content);
        } catch (JsonProcessingException ex) {
            String path = jsonPathFromMessage(ex.getOriginalMessage());
            if (path == null || "$".equals(path)) {
                path = pathAtFailure(content);
            }
            return PackMetaResult.failure(new PackMetaError(file, path, PackMetaErrorKind.JSON_SYNTAX,
                    ex.getOriginalMessage(), MOJIRA_JSON_SYNTAX));
        }
        if (root == null || !root.isObject()) {
            return PackMetaResult.failure(new PackMetaError(file, "$", PackMetaErrorKind.INVALID_STRUCTURE,
                    "pack.mcmeta 顶层必须是 JSON 对象", null));
        }
        JsonNode pack = root.get("pack");
        if (pack == null || !pack.isObject()) {
            return PackMetaResult.failure(new PackMetaError(file, "$.pack",
                    PackMetaErrorKind.INVALID_STRUCTURE, "缺少 pack 对象", null));
        }
        JsonNode format = pack.get("pack_format");
        if (format == null) {
            return PackMetaResult.failure(new PackMetaError(file, "$.pack.pack_format",
                    PackMetaErrorKind.MISSING_PACK_FORMAT, "缺少 pack_format", null));
        }
        if (!format.isInt() || format.asInt() <= 0) {
            return PackMetaResult.failure(new PackMetaError(file, "$.pack.pack_format",
                    PackMetaErrorKind.INVALID_STRUCTURE, "pack_format 必须是正整数: " + format, null));
        }
        Map<String, Object> raw = MAPPER.convertValue(root, new TypeReference<Map<String, Object>>() {
        });
        PackMeta meta = new PackMeta(format.asInt(), description(pack.get("description")),
                supportedFormats(pack.get("supported_formats")), namespaceHints(pack), raw);
        return PackMetaResult.ok(meta);
    }

    /**
     * 从一行崩溃日志里认出三类 pack 失败。
     *
     * <p>JSON 异常必须带包上下文（pack.mcmeta / datapack / data pack）才算，避免把模组配置解析
     * 失败误判成数据包问题；世界加载失败要求同时出现加载失败措辞。</p>
     */
    public static Optional<PackMetaError> fromLog(String line) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }
        String lower = line.toLowerCase(Locale.ROOT);
        if (lower.contains("couldn't find pack meta") || lower.contains("could not find pack meta")) {
            return Optional.of(new PackMetaError(fileFromLine(line), "$.pack",
                    PackMetaErrorKind.MISSING_META, line.trim(), MOJIRA_MISSING_META));
        }
        boolean jsonFailure = line.contains("JsonSyntaxException") || line.contains("MalformedJsonException");
        if (!jsonFailure || !hasPackContext(lower)) {
            return Optional.empty();
        }
        boolean worldLoad = lower.contains("failed to load datapacks")
                || lower.contains("failed to load data packs")
                || lower.contains("failed to load pack")
                || lower.contains("failed to load world")
                || lower.contains("error loading world");
        PackMetaErrorKind kind = worldLoad ? PackMetaErrorKind.WORLD_LOAD_FAILURE : PackMetaErrorKind.JSON_SYNTAX;
        String mojira = worldLoad ? MOJIRA_WORLD_LOAD : MOJIRA_JSON_SYNTAX;
        String path = jsonPathFromMessage(line);
        return Optional.of(new PackMetaError(fileFromLine(line), path == null ? "$" : path, kind,
                line.trim(), mojira));
    }

    private static boolean hasPackContext(String lower) {
        return lower.contains(FILE_NAME) || lower.contains("datapack") || lower.contains("data pack")
                || lower.contains("数据包") || lower.contains("$.pack");
    }

    private static String description(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isObject() && node.hasNonNull("text")) {
            return node.get("text").asText();
        }
        if (node.isArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode child : node) {
                String part = description(child);
                if (!part.isBlank()) {
                    parts.add(part);
                }
            }
            return String.join("", parts);
        }
        return node.toString();
    }

    private static List<PackFormatRange> supportedFormats(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (node.isNumber()) {
            return List.of(PackFormatRange.single(node.asInt()));
        }
        if (node.isArray()) {
            if (node.size() == 2 && node.get(0).isNumber() && node.get(1).isNumber()) {
                return List.of(new PackFormatRange(node.get(0).asInt(), node.get(1).asInt()));
            }
            List<PackFormatRange> ranges = new ArrayList<>();
            for (JsonNode child : node) {
                if (child.isNumber()) {
                    ranges.add(PackFormatRange.single(child.asInt()));
                }
            }
            return List.copyOf(ranges);
        }
        if (node.isObject()) {
            JsonNode min = node.get("min_inclusive");
            JsonNode max = node.get("max_inclusive");
            if (min != null && max != null && min.isNumber() && max.isNumber()) {
                return List.of(new PackFormatRange(min.asInt(), max.asInt()));
            }
        }
        return List.of();
    }

    private static List<String> namespaceHints(JsonNode pack) {
        JsonNode filter = pack.get("filter");
        if (filter == null || !filter.isArray()) {
            return List.of();
        }
        LinkedHashSet<String> namespaces = new LinkedHashSet<>();
        for (JsonNode entry : filter) {
            JsonNode namespace = entry.get("namespace");
            if (namespace != null && namespace.isTextual() && !namespace.asText().isBlank()) {
                namespaces.add(namespace.asText());
            }
        }
        return List.copyOf(namespaces);
    }

    /** 报错文本里常带 Gson 的 {@code path $.pack.description}，优先用它。 */
    private static String jsonPathFromMessage(String message) {
        if (message == null) {
            return null;
        }
        Matcher matcher = JSON_PATH.matcher(message);
        return matcher.find() ? matcher.group() : null;
    }

    /**
     * 跟踪解析求失败点路径。
     *
     * <p>Jackson 的语法异常通常不填 path，这里逐 token 维护对象字段与数组下标栈，异常发生时
     * 栈顶即出错位置，例如 {@code $.pack.description}。</p>
     */
    private static String pathAtFailure(String content) {
        Deque<Frame> stack = new ArrayDeque<>();
        try (JsonParser parser = MAPPER.createParser(content)) {
            JsonToken token;
            while ((token = parser.nextToken()) != null) {
                switch (token) {
                    case START_OBJECT -> stack.push(new Frame(false));
                    case START_ARRAY -> stack.push(new Frame(true));
                    case END_OBJECT, END_ARRAY -> {
                        stack.pop();
                        Frame parent = stack.peek();
                        if (parent != null && parent.array) {
                            parent.index++;
                        }
                    }
                    case FIELD_NAME -> {
                        Frame top = stack.peek();
                        if (top != null) {
                            top.field = parser.currentName();
                        }
                    }
                    case VALUE_STRING, VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT, VALUE_TRUE, VALUE_FALSE, VALUE_NULL -> {
                        Frame top = stack.peek();
                        if (top != null && top.array) {
                            top.index++;
                        }
                    }
                    default -> {
                    }
                }
            }
        } catch (IOException ex) {
            return pathOf(stack);
        }
        return "$";
    }

    private static String pathOf(Deque<Frame> stack) {
        StringBuilder path = new StringBuilder("$");
        Iterator<Frame> rootFirst = stack.descendingIterator();
        while (rootFirst.hasNext()) {
            Frame frame = rootFirst.next();
            if (frame.array) {
                path.append('[').append(frame.index).append(']');
            } else if (frame.field != null) {
                path.append('.').append(frame.field);
            }
        }
        return path.toString();
    }

    private static String fileFromLine(String line) {
        Matcher token = PACK_META_TOKEN.matcher(line);
        if (token.find()) {
            return trimToken(token.group(1));
        }
        Matcher directory = PACK_META_DIR.matcher(line);
        if (directory.find()) {
            return trimToken(directory.group(1));
        }
        return FILE_NAME;
    }

    private static String trimToken(String raw) {
        String trimmed = raw;
        boolean changed = true;
        while (changed && !trimmed.isEmpty()) {
            changed = false;
            for (String prefix : TRIM_CHARS) {
                if (trimmed.startsWith(prefix)) {
                    trimmed = trimmed.substring(prefix.length());
                    changed = true;
                }
                if (trimmed.endsWith(prefix)) {
                    trimmed = trimmed.substring(0, trimmed.length() - prefix.length());
                    changed = true;
                }
            }
        }
        return trimmed.isEmpty() ? FILE_NAME : trimmed;
    }

    /** 跟踪解析用的栈帧：数组记下标，对象记当前字段。 */
    private static final class Frame {
        private final boolean array;
        private String field;
        private int index;

        private Frame(boolean array) {
            this.array = array;
        }
    }
}
