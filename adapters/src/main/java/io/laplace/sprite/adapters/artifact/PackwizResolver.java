package io.laplace.sprite.adapters.artifact;

import io.laplace.sprite.adapters.support.SimpleToml;
import io.laplace.sprite.core.model.Artifact;
import io.laplace.sprite.core.model.ArtifactDependency;
import io.laplace.sprite.core.model.ArtifactRole;
import io.laplace.sprite.core.model.ArtifactSource;
import io.laplace.sprite.core.model.ArtifactSpec;
import io.laplace.sprite.core.spi.ArtifactResolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * packwiz 元数据解析（14.5）：{@code pack.toml} / {@code index.toml} / {@code *.pw.toml}。
 *
 * <p>解析顺序是 pack.toml → index.toml → 各 pw.toml；闭包按 {@code side} 与 {@code optional} 过滤，
 * 默认只取服务端要装的部分（side=server|both 且非 optional）。</p>
 */
public final class PackwizResolver implements ArtifactResolver {

    private static final String FALLBACK_INDEX = "index.toml";

    private final boolean includeClientSide;
    private final boolean includeOptional;

    public PackwizResolver() {
        this(false, false);
    }

    public PackwizResolver(boolean includeClientSide, boolean includeOptional) {
        this.includeClientSide = includeClientSide;
        this.includeOptional = includeOptional;
    }

    @Override
    public Artifact resolve(ArtifactSpec spec) {
        Path path = Path.of(spec.coordinate());
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("packwiz 元数据不存在: " + spec.coordinate());
        }
        try {
            return parseMeta(path.getFileName().toString(), Files.readString(path, StandardCharsets.UTF_8),
                    spec.role());
        } catch (IOException ex) {
            throw new UncheckedIOException("读取 packwiz 元数据失败: " + spec.coordinate(), ex);
        }
    }

    @Override
    public List<Artifact> resolveDependencies(Artifact artifact) {
        return artifact.dependencies().stream()
                .map(dependency -> new ArtifactSpec(ArtifactSource.PACKWIZ, dependency.id(), null,
                        ArtifactRole.MOD, Map.of()))
                .map(spec -> deterministic(spec.coordinate()))
                .toList();
    }

    /** 解析 pack.toml 引用的文件清单（index.toml 的 [[files]]）。 */
    public List<String> fileList(String indexToml) {
        List<String> files = new ArrayList<>();
        for (Map<String, String> table : SimpleToml.parse(indexToml).array("files")) {
            String file = table.get("file");
            if (file != null && !file.isBlank()) {
                files.add(file);
            }
        }
        return List.copyOf(files);
    }

    /**
     * 闭包解析：documents 是「文件名 → 文件内容」的内存视图，避免解析过程依赖磁盘布局。
     *
     * @param packToml  pack.toml 内容
     * @param documents 摘要引用的文件（index.toml 与各 *.pw.toml）
     */
    public List<Artifact> closure(String packToml, Map<String, String> documents) {
        SimpleToml pack = SimpleToml.parse(packToml);
        String indexFile = pack.scalar("index", "file");
        if (indexFile == null || !documents.containsKey(indexFile)) {
            indexFile = FALLBACK_INDEX;
        }
        String indexContent = documents.get(indexFile);
        if (indexContent == null) {
            throw new IllegalArgumentException("缺少 index.toml（pack.toml 的 [index].file 未命中）");
        }
        List<Artifact> artifacts = new ArrayList<>();
        for (String file : fileList(indexContent)) {
            String content = documents.get(file);
            if (content == null) {
                artifacts.add(deterministic(file));
                continue;
            }
            Artifact artifact = parseMeta(file, content, ArtifactRole.MOD);
            if (included(artifact)) {
                artifacts.add(artifact);
            }
        }
        artifacts.sort((a, b) -> a.id().compareTo(b.id()));
        return List.copyOf(artifacts);
    }

    /** 解析单个 *.pw.toml。 */
    public Artifact parseMeta(String fileName, String content, ArtifactRole role) {
        SimpleToml meta = SimpleToml.parse(content);
        String name = meta.scalar("name");
        String modId = meta.scalar("update.modrinth", "mod-id");
        if (modId == null) {
            modId = meta.scalar("update.curseforge", "project-id");
        }
        String id = modId != null ? modId : (name != null ? name : fileName.replaceAll("\\.pw\\.toml$", ""));
        String version = meta.scalar("version");
        if (version == null) {
            version = meta.scalar("update.modrinth", "version");
        }
        String side = meta.scalar("side");
        boolean optional = "true".equalsIgnoreCase(meta.scalar("option", "optional"))
                || "true".equalsIgnoreCase(meta.scalar("optional"));
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("filename", orEmpty(meta.scalar("filename")));
        metadata.put("side", side == null ? "both" : side);
        metadata.put("optional", Boolean.toString(optional));
        metadata.put("hash-format", orEmpty(meta.scalar("download", "hash-format")));
        metadata.put("hash", orEmpty(meta.scalar("download", "hash")));
        return new Artifact(id, name == null ? id : name, version == null ? "unknown" : version,
                ArtifactSource.PACKWIZ, orEmpty(meta.scalar("download", "url")), null,
                role == null ? ArtifactRole.MOD : role, declares(meta), metadata);
    }

    private static List<ArtifactDependency> declares(SimpleToml meta) {
        List<ArtifactDependency> dependencies = new ArrayList<>();
        for (Map<String, String> table : meta.array("depends")) {
            String id = table.get("mod-id") != null ? table.get("mod-id") : table.get("id");
            if (id != null) {
                dependencies.add(ArtifactDependency.hard(id));
            }
        }
        return List.copyOf(dependencies);
    }

    private boolean included(Artifact artifact) {
        String side = artifact.metadata().getOrDefault("side", "both");
        boolean optional = Boolean.parseBoolean(artifact.metadata().getOrDefault("optional", "false"));
        if (optional && !includeOptional) {
            return false;
        }
        boolean serverSide = side.equals("server") || side.equals("both");
        return serverSide || includeClientSide;
    }

    private static Artifact deterministic(String file) {
        String id = file.replaceAll("^mods/", "").replaceAll("\\.pw\\.toml$", "");
        return new Artifact(id, id, "unknown", ArtifactSource.PACKWIZ, file, null, ArtifactRole.MOD,
                List.of(), Map.of("offline", "true", "synthesised", "true"));
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
