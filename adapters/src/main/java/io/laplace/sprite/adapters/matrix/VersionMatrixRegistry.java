package io.laplace.sprite.adapters.matrix;

import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.model.VersionMatrixEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 版本矩阵注册表（14.7）：运行时拉取 + 本地快照 + 手动 pin 三者合并。
 *
 * <p>优先级 pin &gt; runtime &gt; snapshot。默认注册表用离线快照构建，不碰网络；
 * 需要真实拉取时自行构造来源并调用 {@link #refresh}。</p>
 */
public final class VersionMatrixRegistry {

    private final List<VersionMatrixSource> sources;
    private final List<VersionMatrixEntry> runtime = new CopyOnWriteArrayList<>();
    private final List<VersionMatrixEntry> localPins = new CopyOnWriteArrayList<>();

    public VersionMatrixRegistry(List<VersionMatrixSource> sources) {
        this.sources = List.copyOf(sources);
    }

    /** 四个加载器来源，离线模式（只读本地快照）。 */
    public static VersionMatrixRegistry defaults() {
        return defaults(false);
    }

    public static VersionMatrixRegistry defaults(boolean networkEnabled) {
        return new VersionMatrixRegistry(List.of(
                new FabricMetaSource(networkEnabled, null),
                new QuiltMetaSource(networkEnabled, null),
                new ForgePromotionsSource(networkEnabled, null),
                new NeoForgeProjectsSource(networkEnabled, null)));
    }

    public List<VersionMatrixSource> sources() {
        return sources;
    }

    public Optional<VersionMatrixSource> sourceFor(PlatformFamily family) {
        return sources.stream().filter(source -> source.family() == family).findFirst();
    }

    /** 本地快照合并（不碰网络）。 */
    public List<VersionMatrixEntry> snapshot(PlatformFamily family) {
        List<VersionMatrixEntry> merged = new ArrayList<>();
        for (VersionMatrixSource source : sources) {
            merged.addAll(source.snapshot(family));
        }
        return List.copyOf(merged);
    }

    /** 运行时拉取并并入运行期条目。 */
    public List<VersionMatrixEntry> refresh(PlatformFamily family) {
        List<VersionMatrixEntry> fetched = new ArrayList<>();
        for (VersionMatrixSource source : sources) {
            if (source.family() != family) {
                continue;
            }
            List<VersionMatrixEntry> entries = source.fetch(family);
            runtime.removeIf(entry -> entry.family() == family);
            runtime.addAll(entries);
            fetched.addAll(entries);
        }
        return List.copyOf(fetched);
    }

    /** 合并视图：pin 优先，其次运行期，最后快照。 */
    public List<VersionMatrixEntry> entries(PlatformFamily family) {
        List<VersionMatrixEntry> merged = new ArrayList<>();
        pinned(family).stream().filter(entry -> entry.family() == family).forEach(merged::add);
        runtime.stream().filter(entry -> entry.family() == family).forEach(merged::add);
        merged.addAll(snapshot(family));
        return List.copyOf(merged);
    }

    public List<VersionMatrixEntry> pinned(PlatformFamily family) {
        List<VersionMatrixEntry> merged = new ArrayList<>(localPins);
        for (VersionMatrixSource source : sources) {
            if (source instanceof AbstractVersionMatrixSource concrete) {
                merged.addAll(concrete.pinned());
            }
        }
        return merged.stream().filter(entry -> entry.family() == family).toList();
    }

    public void pin(VersionMatrixEntry entry) {
        Optional<VersionMatrixSource> source = sourceFor(entry.family());
        if (source.isPresent()) {
            source.get().pin(entry);
        } else {
            localPins.add(entry);
        }
    }

    /** 该家族是否登记过任何条目。 */
    public boolean supports(PlatformFamily family) {
        return !entries(family).isEmpty();
    }

    /** 三维矩阵键是否在矩阵内：Minecraft 版本必须精确命中，加载器版本精确命中（条目未声明则不限）。 */
    public boolean supports(PlatformKey key) {
        return entries(key.family()).stream().anyMatch(entry -> matches(entry, key));
    }

    private static boolean matches(VersionMatrixEntry entry, PlatformKey key) {
        if (!entry.minecraftVersion().equals(key.minecraftVersion())) {
            return false;
        }
        if (entry.loaderVersion() == null) {
            return true;
        }
        return entry.loaderVersion().equals(key.loaderVersion());
    }
}
