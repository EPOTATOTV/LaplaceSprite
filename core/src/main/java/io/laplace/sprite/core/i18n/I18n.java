package io.laplace.sprite.core.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * 进程级多语言门面，不依赖 Spring，也不依赖调用方注入。
 *
 * <p>内核文案从 classpath 的 {@code /i18n/messages_*.properties} 懒加载；业务插件在
 * {@code onLoad} 里通过 {@link #registerPluginBundle} 注册自己的文案。插件 key 统一加
 * {@code pluginId.} 前缀，避免和内核 key 撞车。</p>
 */
public final class I18n {

    private static final String RESOURCE_PATTERN = "/i18n/messages_%s.properties";

    private static final I18n INSTANCE = new I18n();

    private final Object lock = new Object();

    private final Map<Language, Map<String, String>> coreMessages = new EnumMap<>(Language.class);

    /** 语言 -> 插件 id -> 该插件的完整语言包。 */
    private final Map<Language, Map<String, Map<String, String>>> pluginPacks = new EnumMap<>(Language.class);

    /** 语言 -> 所有插件包展平后的视图，查找时少一层遍历。 */
    private final Map<Language, Map<String, String>> pluginMessages = new EnumMap<>(Language.class);

    private volatile Language language = Language.ZH_CN;

    private I18n() {
    }

    public static I18n get() {
        return INSTANCE;
    }

    public void setLanguage(Language language) {
        this.language = Objects.requireNonNull(language, "language");
    }

    public Language language() {
        return language;
    }

    /**
     * 取文案。当前语言缺失时回退到英文，再缺失直接返回 key。
     *
     * @param key  文案 key
     * @param args 传给 {@link MessageFormat} 的参数，可为空
     */
    public String t(String key, Object... args) {
        Objects.requireNonNull(key, "key");
        String pattern = message(key, language);
        if (pattern == null && language != Language.EN_US) {
            pattern = message(key, Language.EN_US);
        }
        if (pattern == null) {
            return key;
        }
        if (args == null || args.length == 0) {
            return pattern;
        }
        return MessageFormat.format(pattern, args);
    }

    /**
     * 注册插件语言包。key 没有 {@code pluginId.} 前缀时自动补上；重复注册整包覆盖旧值。
     */
    public void registerPluginBundle(String pluginId, Language language, Map<String, String> messages) {
        Objects.requireNonNull(language, "language");
        if (pluginId == null || pluginId.isBlank()) {
            throw new IllegalArgumentException("pluginId 不能为空");
        }
        String prefix = pluginId + ".";
        Map<String, String> prepared = new HashMap<>();
        if (messages != null) {
            messages.forEach((key, value) -> {
                Objects.requireNonNull(key, "key");
                Objects.requireNonNull(value, "value");
                prepared.put(key.startsWith(prefix) ? key : prefix + key, value);
            });
        }
        synchronized (lock) {
            pluginPacks.computeIfAbsent(language, lang -> new HashMap<>()).put(pluginId, Map.copyOf(prepared));
            pluginMessages.put(language, flatten(language));
        }
    }

    /** 当前语言下已注册的 key 数（内核 + 插件）。 */
    public int keyCount() {
        return messages(language).size();
    }

    /** 丢弃内核文案缓存；下次查找重新读 classpath。 */
    public void reload() {
        synchronized (lock) {
            coreMessages.clear();
        }
    }

    /** 恢复默认语言并清空所有插件语言包。 */
    public void reset() {
        synchronized (lock) {
            language = Language.ZH_CN;
            pluginPacks.clear();
            pluginMessages.clear();
            coreMessages.clear();
        }
    }

    private String message(String key, Language target) {
        String plugin = pluginMessages(target).get(key);
        return plugin != null ? plugin : coreMessages(target).get(key);
    }

    private Map<String, String> pluginMessages(Language target) {
        synchronized (lock) {
            return pluginMessages.getOrDefault(target, Map.of());
        }
    }

    private Map<String, String> coreMessages(Language target) {
        synchronized (lock) {
            Map<String, String> cached = coreMessages.get(target);
            if (cached == null) {
                cached = load(target);
                coreMessages.put(target, cached);
            }
            return cached;
        }
    }

    private Map<String, String> messages(Language target) {
        synchronized (lock) {
            Map<String, String> merged = new HashMap<>(coreMessages(target));
            merged.putAll(pluginMessages(target));
            return Map.copyOf(merged);
        }
    }

    private Map<String, String> flatten(Language target) {
        Map<String, String> merged = new HashMap<>();
        pluginPacks.getOrDefault(target, Map.of()).values().forEach(merged::putAll);
        return Map.copyOf(merged);
    }

    private static Map<String, String> load(Language target) {
        String path = RESOURCE_PATTERN.formatted(target.tag().replace('-', '_'));
        try (InputStream stream = I18n.class.getResourceAsStream(path)) {
            if (stream == null) {
                return Map.of();
            }
            Properties properties = new Properties();
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            Map<String, String> messages = new HashMap<>();
            properties.forEach((key, value) -> messages.put(String.valueOf(key), String.valueOf(value)));
            return Map.copyOf(messages);
        } catch (IOException ex) {
            return Map.of();
        }
    }
}
