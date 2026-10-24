package io.laplace.sprite.core.i18n;

import java.util.Locale;
import java.util.Optional;

/**
 * 内核支持的语言。标签统一用 BCP 47 的短横线写法，资源文件用下划线写法。
 */
public enum Language {

    ZH_CN("zh-CN"),
    EN_US("en-US");

    private final String tag;

    Language(String tag) {
        this.tag = tag;
    }

    /** BCP 47 标签，例如 {@code zh-CN}。 */
    public String tag() {
        return tag;
    }

    /** 接受 {@code zh-CN} / {@code zh} / {@code en-US} / {@code en}，大小写和下划线不敏感。 */
    public static Optional<Language> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim().replace('_', '-').toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "zh-cn", "zh" -> Optional.of(ZH_CN);
            case "en-us", "en" -> Optional.of(EN_US);
            default -> Optional.empty();
        };
    }
}
