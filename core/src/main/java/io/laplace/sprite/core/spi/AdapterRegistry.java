package io.laplace.sprite.core.spi;

import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 适配器注册与版本回退（14.4）。
 *
 * <p>按「平台/加载器 × Minecraft 版本 × 加载器版本」注册；矩阵不匹配时按兼容链回退并输出告警。</p>
 */
public final class AdapterRegistry {

    private final List<LaplaceAdapter> adapters = new CopyOnWriteArrayList<>();
    private final List<String> warnings = new CopyOnWriteArrayList<>();

    public void register(LaplaceAdapter adapter) {
        adapters.add(adapter);
    }

    public List<LaplaceAdapter> all() {
        return List.copyOf(adapters);
    }

    public Optional<LaplaceAdapter> byId(String id) {
        return adapters.stream().filter(a -> a.id().equals(id)).findFirst();
    }

    public List<LaplaceAdapter> byFamily(PlatformFamily family) {
        return adapters.stream().filter(a -> a.family() == family).toList();
    }

    /**
     * 精确匹配优先；否则按兼容链回退（Purpur→Paper→Bukkit；Quilt→Fabric；PNX→Nukkit）。
     */
    public Optional<LaplaceAdapter> resolve(PlatformKey key) {
        Optional<LaplaceAdapter> exact = match(key, key.family());
        if (exact.isPresent()) {
            return exact;
        }
        List<String> trail = new ArrayList<>();
        for (PlatformFamily fallback : key.family().compatChain()) {
            trail.add(fallback.name().toLowerCase());
            Optional<LaplaceAdapter> candidate = match(key, fallback);
            if (candidate.isPresent()) {
                warnings.add("矩阵无「%s」专用适配器，回退到「%s」，链路 %s".formatted(
                        key.id(), candidate.get().id(), String.join("→", trail)));
                return candidate;
            }
        }
        warnings.add("矩阵无可用适配器: %s".formatted(key.id()));
        return Optional.empty();
    }

    private Optional<LaplaceAdapter> match(PlatformKey key, PlatformFamily family) {
        return adapters.stream()
                .filter(a -> a.family() == family)
                .filter(a -> a.supportRange().supports(key))
                .findFirst();
    }

    /** 降级告警（14.8 验收：版本矩阵匹配无降级告警）。 */
    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    public void clearWarnings() {
        warnings.clear();
    }

    public int size() {
        return adapters.size();
    }
}
