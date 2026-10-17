package io.laplace.sprite.replay;

import io.laplace.sprite.core.io.Json;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 世界快照索引（15.10）：把四元组标签、快照目录与 .mcpr 场景双向绑定。
 *
 * <p>索引是 JSON 文件，重启后按 {@code bindings} 数组恢复。绑定按场景 id、快照路径、
 * 录像路径三个键去重：重复绑定同一场景会替换旧值，保证「按场景 id 反查快照」只有一条结果。</p>
 *
 * <p>「bug 现场 → 回归用例」的路径：用 {@link #findBySceneId} 从崩溃现场的 sceneId 反查标签，
 * 再取 {@link #snapshotPathOfScene} 恢复对应版本的世界基线。</p>
 */
public final class SnapshotRegistry {

    public static final int INDEX_VERSION = 1;
    public static final String DEFAULT_INDEX_FILE = "world-snapshots.json";

    /**
     * 一条绑定。
     *
     * @param tag          四元组标签
     * @param snapshotPath 快照目录（绝对路径，规范化后存储）
     * @param mcprPath     对应录像路径；没有时为空串
     */
    public record Binding(WorldSnapshotTag tag, String snapshotPath, String mcprPath) {

        public Binding {
            tag = Objects.requireNonNull(tag, "tag");
            snapshotPath = requireText(snapshotPath, "snapshotPath");
            mcprPath = mcprPath == null ? "" : mcprPath;
        }
    }

    /** 持久化结构。 */
    public record Index(int version, List<Binding> bindings) {

        public Index {
            bindings = bindings == null ? List.of() : List.copyOf(bindings);
        }
    }

    private final Path indexFile;
    private final Map<String, Binding> byScene = new LinkedHashMap<>();
    private final Map<String, Binding> bySnapshot = new LinkedHashMap<>();
    private final Map<String, Binding> byMcpr = new LinkedHashMap<>();

    public SnapshotRegistry() {
        this(null);
    }

    public SnapshotRegistry(Path indexFile) {
        this.indexFile = indexFile;
    }

    /** 从索引文件恢复；文件不存在时返回空索引。 */
    public static SnapshotRegistry open(Path indexFile) {
        SnapshotRegistry registry = new SnapshotRegistry(indexFile);
        registry.reload();
        return registry;
    }

    public static SnapshotRegistry load(Path indexFile) {
        return open(indexFile);
    }

    public Path indexFile() {
        return indexFile;
    }

    public void reload() {
        if (indexFile == null || !Files.isRegularFile(indexFile)) {
            return;
        }
        Index index = Json.readFile(indexFile, Index.class);
        byScene.clear();
        bySnapshot.clear();
        byMcpr.clear();
        for (Binding binding : index.bindings()) {
            bind(binding.tag(), Path.of(binding.snapshotPath()),
                    binding.mcprPath().isEmpty() ? null : Path.of(binding.mcprPath()));
        }
    }

    public void save() {
        if (indexFile == null) {
            throw new IllegalStateException("未指定索引文件，无法持久化快照索引");
        }
        Json.writeFile(indexFile, new Index(INDEX_VERSION, bindings()));
    }

    /** 绑定四元组到快照目录与录像；同一场景或同一路径的旧绑定会被替换。 */
    public void bind(WorldSnapshotTag tag, Path snapshotPath, Path mcprPath) {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(snapshotPath, "snapshotPath");
        String snapshotKey = pathKey(snapshotPath);
        String mcprKey = mcprPath == null ? "" : pathKey(mcprPath);
        unbind(tag.sceneId(), snapshotKey, mcprKey);
        Binding binding = new Binding(tag, snapshotKey, mcprKey);
        byScene.put(binding.tag().sceneId(), binding);
        bySnapshot.put(binding.snapshotPath(), binding);
        if (!binding.mcprPath().isEmpty()) {
            byMcpr.put(binding.mcprPath(), binding);
        }
    }

    public Optional<WorldSnapshotTag> findBySceneId(String sceneId) {
        if (sceneId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byScene.get(sceneId)).map(Binding::tag);
    }

    public Optional<WorldSnapshotTag> tagOf(Path snapshotPath) {
        if (snapshotPath == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(bySnapshot.get(pathKey(snapshotPath))).map(Binding::tag);
    }

    public Optional<Binding> bindingOfScene(String sceneId) {
        return sceneId == null ? Optional.empty() : Optional.ofNullable(byScene.get(sceneId));
    }

    public Optional<Path> snapshotPathOfScene(String sceneId) {
        return bindingOfScene(sceneId).map(binding -> Path.of(binding.snapshotPath()));
    }

    public Optional<Path> mcprPathOfScene(String sceneId) {
        return bindingOfScene(sceneId)
                .filter(binding -> !binding.mcprPath().isEmpty())
                .map(binding -> Path.of(binding.mcprPath()));
    }

    public List<Binding> bindings() {
        return List.copyOf(byScene.values());
    }

    private void unbind(String sceneId, String snapshotPath, String mcprPath) {
        Binding[] candidates = {byScene.remove(sceneId), bySnapshot.remove(snapshotPath),
                mcprPath.isEmpty() ? null : byMcpr.remove(mcprPath)};
        for (Binding binding : candidates) {
            if (binding != null) {
                byScene.remove(binding.tag().sceneId(), binding);
                bySnapshot.remove(binding.snapshotPath(), binding);
                byMcpr.remove(binding.mcprPath(), binding);
            }
        }
    }

    private static String pathKey(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value;
    }
}
