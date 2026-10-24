package io.laplace.sprite.game;

import com.fasterxml.jackson.databind.JsonNode;
import io.laplace.sprite.core.io.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 实例管理器：每个实例复制一份 client.jar，避免多个实例共享可写文件。
 */
public final class GameInstanceManager {

    private static final Pattern INSTANCE_ID = Pattern.compile("[A-Za-z0-9._-]+");

    private final GameCache cache;
    private final Path instancesRoot;
    private final GameMetadataResolver resolver;

    public GameInstanceManager(GameCache cache) {
        this(cache, cache.root().resolve("instances"));
    }

    public GameInstanceManager(GameCache cache, Path instancesRoot) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.instancesRoot = Objects.requireNonNull(instancesRoot, "instancesRoot")
                .toAbsolutePath().normalize();
        this.resolver = new GameMetadataResolver();
    }

    /**
     * 从缓存创建实例。缓存不完整时拒绝创建，实例目录已存在时也拒绝，避免覆盖已有文件。
     */
    public GameInstance create(String version, String instanceId) throws IOException {
        if (!cache.isCached(version)) {
            throw new IllegalStateException("版本 " + version + " 尚未缓存，先执行 laplace game download "
                    + version);
        }
        Path instanceRoot = instanceDir(instanceId);
        Files.createDirectories(instancesRoot);
        try {
            Files.createDirectory(instanceRoot);
        } catch (FileAlreadyExistsException ex) {
            throw new IllegalArgumentException("实例已存在: " + instanceId, ex);
        }

        Path clientJar = instanceRoot.resolve(GameCache.CLIENT_JAR);
        Files.copy(cache.clientJar(version), clientJar, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(cache.versionFile(version), instanceRoot.resolve(GameCache.VERSION_JSON),
                StandardCopyOption.REPLACE_EXISTING);
        Path cachedMetadata = cache.metadataFile(version);
        if (Files.isRegularFile(cachedMetadata)) {
            Files.copy(cachedMetadata, instanceRoot.resolve(GameCache.METADATA_JSON),
                    StandardCopyOption.REPLACE_EXISTING);
        }

        String versionJson = Files.readString(cache.versionFile(version), StandardCharsets.UTF_8);
        GameVersionMetadata metadata = resolver.resolve(version, versionJson);
        return new GameInstance(instanceId, version, instanceRoot, clientJar, metadata);
    }

    /** 列出 instances 目录下能读到 version.json 的实例。 */
    public List<GameInstance> list() throws IOException {
        if (!Files.isDirectory(instancesRoot)) {
            return List.of();
        }
        List<GameInstance> instances = new ArrayList<>();
        List<Path> directories;
        try (Stream<Path> paths = Files.list(instancesRoot)) {
            directories = paths.filter(Files::isDirectory).sorted().toList();
        }
        for (Path directory : directories) {
            Path versionFile = directory.resolve(GameCache.VERSION_JSON);
            if (!Files.isRegularFile(versionFile)) {
                continue;
            }
            String versionJson = Files.readString(versionFile, StandardCharsets.UTF_8);
            String instanceId = directory.getFileName().toString();
            GameVersionMetadata metadata = resolver.resolve(metadataVersion(versionJson, instanceId),
                    versionJson);
            instances.add(new GameInstance(instanceId, metadata.id(), directory,
                    directory.resolve(GameCache.CLIENT_JAR), metadata));
        }
        return List.copyOf(instances);
    }

    private Path instanceDir(String instanceId) {
        if (instanceId == null || !INSTANCE_ID.matcher(instanceId).matches()
                || ".".equals(instanceId) || "..".equals(instanceId)) {
            throw new IllegalArgumentException("非法实例 id: " + instanceId);
        }
        Path directory = instancesRoot.resolve(instanceId).normalize();
        if (!directory.startsWith(instancesRoot)) {
            throw new IllegalArgumentException("实例 id 逃出 instances 目录: " + instanceId);
        }
        return directory;
    }

    private static String metadataVersion(String versionJson, String fallback) {
        try {
            JsonNode root = Json.jsonMapper().readTree(versionJson);
            if (root != null) {
                JsonNode id = root.get("id");
                if (id != null && id.isTextual() && !id.asText().isBlank()) {
                    return id.asText();
                }
            }
        } catch (IOException ex) {
            return fallback;
        }
        return fallback;
    }
}
