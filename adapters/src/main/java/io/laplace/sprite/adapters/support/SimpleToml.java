package io.laplace.sprite.adapters.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 TOML 读取器（14.5 packwiz / 14.3 mods.toml）。
 *
 * <p>只认三类结构：{@code key = value}、{@code [section]}、{@code [[array]]}；
 * 值一律保留原始字符串，去掉包裹的引号。故意不引 TOML 依赖——这两处用到的语法就这么多。</p>
 */
public final class SimpleToml {

    private final Map<String, String> root = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> sections = new LinkedHashMap<>();
    private final Map<String, List<Map<String, String>>> arrays = new LinkedHashMap<>();

    private SimpleToml() {
    }

    public static SimpleToml parse(String content) {
        SimpleToml toml = new SimpleToml();
        Map<String, String> current = toml.root;
        for (String raw : content.split("\\R")) {
            String line = stripComment(raw).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("[[") && line.endsWith("]]")) {
                String name = line.substring(2, line.length() - 2).trim();
                Map<String, String> table = new LinkedHashMap<>();
                toml.arrays.computeIfAbsent(name, k -> new ArrayList<>()).add(table);
                current = table;
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                String name = line.substring(1, line.length() - 1).trim();
                current = toml.sections.computeIfAbsent(name, k -> new LinkedHashMap<>());
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            current.put(line.substring(0, eq).trim(), unquote(line.substring(eq + 1).trim()));
        }
        return toml;
    }

    public String scalar(String key) {
        return root.get(key);
    }

    public String scalar(String section, String key) {
        return sections.getOrDefault(section, Map.of()).get(key);
    }

    public Map<String, String> section(String name) {
        return Map.copyOf(sections.getOrDefault(name, Map.of()));
    }

    public Map<String, String> root() {
        return Map.copyOf(root);
    }

    public List<Map<String, String>> array(String name) {
        List<Map<String, String>> tables = arrays.get(name);
        if (tables == null) {
            return List.of();
        }
        return tables.stream().map(Map::copyOf).toList();
    }

    public boolean containsSection(String name) {
        return sections.containsKey(name);
    }

    /** 去掉注释：只处理引号外的第一个 {@code #}。 */
    private static String stripComment(String line) {
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            } else if (c == '#' && !inSingle && !inDouble) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
