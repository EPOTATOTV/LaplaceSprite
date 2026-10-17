package io.laplace.sprite.mixin;

import io.laplace.sprite.core.io.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Watchdog 归因索引（15.11 P1）。
 *
 * <p>与 crashdiag 的 {@code ClassIndex} 同形：前缀 → modId，最长前缀胜出。
 * 本模块不依赖 crashdiag（依赖方向只允许 mixin → adapters/core），
 * {@link #toClassIndexJson()} 输出 crashdiag 可直接 {@code ClassIndex.fromJson} 的 JSON。</p>
 */
public final class MixinAttributionIndex {

    /**
     * 一条前缀映射。
     *
     * @param prefix 类名或包名前缀
     * @param modId  归属 mod
     */
    public record Entry(String prefix, String modId) {

        public Entry {
            Objects.requireNonNull(prefix, "prefix");
            Objects.requireNonNull(modId, "modId");
        }
    }

    private final List<Entry> entries;

    private MixinAttributionIndex(List<Entry> entries) {
        this.entries = List.copyOf(entries);
    }

    /** 目标类与 mixin 类都建映射：崩溃栈里出现任意一侧都能反查到 mod。 */
    public static MixinAttributionIndex from(List<MixinAttribution> attributions) {
        Objects.requireNonNull(attributions, "attributions");
        Set<Entry> entries = new LinkedHashSet<>();
        for (MixinAttribution attribution : attributions) {
            entries.add(new Entry(attribution.targetClass(), attribution.modId()));
            for (String mixin : attribution.mixins()) {
                entries.add(new Entry(mixin, attribution.modId()));
            }
        }
        return new MixinAttributionIndex(new ArrayList<>(entries));
    }

    /** 最长前缀命中，与 crashdiag 的类索引一致。 */
    public Optional<String> modFor(String className) {
        if (className == null || className.isBlank()) {
            return Optional.empty();
        }
        Entry best = null;
        for (Entry entry : entries) {
            if (className.startsWith(entry.prefix())
                    && (best == null || entry.prefix().length() > best.prefix().length())) {
                best = entry;
            }
        }
        return best == null ? Optional.empty() : Optional.of(best.modId());
    }

    public List<Entry> entries() {
        return entries;
    }

    /** 输出 crashdiag {@code ClassIndex} 的 JSON 形状。 */
    public String toClassIndexJson() {
        List<Map<String, String>> rows = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("prefix", entry.prefix());
            row.put("modId", entry.modId());
            rows.add(row);
        }
        return Json.write(Map.of("entries", rows));
    }
}
