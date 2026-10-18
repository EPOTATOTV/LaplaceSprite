package io.laplace.sprite.cheats.market;

import com.fasterxml.jackson.core.type.TypeReference;
import io.laplace.sprite.core.io.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 本地样本市场（升级实施文档 L-08）：声明式分发包的安装、版本历史与回滚。
 *
 * <p>目录布局：{@code <root>/index.json} 记录每个包当前版本，{@code <root>/<id>/history.json}
 * 记录安装顺序，{@code <root>/<id>/<version>.json} 是签名包本体。回滚只移动指针，
 * 旧版本文件保留，便于再次核对或重新安装。</p>
 */
public final class SampleMarket {

    public static final String INDEX_FILE = "index.json";
    public static final String HISTORY_FILE = "history.json";

    /**
     * 一次安装 / 回滚记录，可直接写入证据链。
     *
     * @param packageId 包 id
     * @param version   版本
     * @param publisher 发布者
     * @param action    install / rollback
     * @param at        发生时间
     * @param sampleIds 该版本的样本 id
     */
    public record InstallRecord(String packageId, String version, String publisher, String action,
                                Instant at, List<String> sampleIds) {

        public InstallRecord {
            sampleIds = sampleIds == null ? List.of() : List.copyOf(sampleIds);
        }

        public String evidencePayload() {
            return Json.write(this);
        }
    }

    private final Path root;
    private final byte[] key;
    private final Map<String, String> index = new LinkedHashMap<>();

    public SampleMarket(Path root, byte[] key) {
        this.root = root;
        this.key = key.clone();
        index.putAll(readIndex());
    }

    /**
     * 安装一个包；签名不合法直接拒绝，不落任何文件。
     *
     * @return 安装记录
     */
    public synchronized InstallRecord install(SamplePackage pkg) {
        if (!SamplePackageSigner.verify(pkg, key)) {
            throw new SecurityException("样本包签名校验失败: " + pkg.id() + "@" + pkg.version());
        }
        Path packageDir = root.resolve(pkg.id());
        writePackage(packageDir.resolve(pkg.version() + ".json"), pkg);
        List<String> history = history(pkg.id());
        if (!history.contains(pkg.version())) {
            history = new ArrayList<>(history);
            history.add(pkg.version());
        }
        writeHistory(pkg.id(), history);
        index.put(pkg.id(), pkg.version());
        writeIndex();
        return record(pkg, "install");
    }

    /**
     * 回滚到上一个安装版本。历史记录保留，游标前移；没有上一个版本时拒绝。
     */
    public synchronized InstallRecord rollback(String packageId) {
        List<String> history = history(packageId);
        String current = index.get(packageId);
        int currentIndex = current == null ? -1 : history.indexOf(current);
        if (currentIndex <= 0) {
            throw new IllegalStateException("包 " + packageId + " 没有可回滚的历史版本");
        }
        String previous = history.get(currentIndex - 1);
        SamplePackage pkg = readPackage(root.resolve(packageId).resolve(previous + ".json"));
        index.put(packageId, previous);
        writeIndex();
        return record(pkg, "rollback");
    }

    public synchronized Optional<SamplePackage> current(String packageId) {
        String version = index.get(packageId);
        if (version == null) {
            return Optional.empty();
        }
        Path file = root.resolve(packageId).resolve(version + ".json");
        return Files.isRegularFile(file) ? Optional.of(readPackage(file)) : Optional.empty();
    }

    /** 某个包安装过的版本，按安装顺序排列。 */
    public synchronized List<String> history(String packageId) {
        Path file = root.resolve(packageId).resolve(HISTORY_FILE);
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            return content.isBlank() ? List.of() : List.of(Json.read(content, String[].class));
        } catch (IOException ex) {
            throw new UncheckedIOException("读取样本包历史失败: " + file, ex);
        }
    }

    /** 当前已安装的包。 */
    public synchronized List<SamplePackage> all() {
        List<SamplePackage> packages = new ArrayList<>();
        for (String id : index.keySet()) {
            current(id).ifPresent(packages::add);
        }
        return List.copyOf(packages);
    }

    public boolean verify(SamplePackage pkg) {
        return SamplePackageSigner.verify(pkg, key);
    }

    public Path root() {
        return root;
    }

    private InstallRecord record(SamplePackage pkg, String action) {
        return new InstallRecord(pkg.id(), pkg.version(), pkg.publisher(), action, Instant.now(),
                pkg.sampleIds());
    }

    private Map<String, String> readIndex() {
        Path file = root.resolve(INDEX_FILE);
        if (!Files.isRegularFile(file)) {
            return Map.of();
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (content.isBlank()) {
                return Map.of();
            }
            return Json.jsonMapper().readValue(content, new TypeReference<Map<String, String>>() {
            });
        } catch (IOException ex) {
            throw new UncheckedIOException("读取样本市场索引失败: " + file, ex);
        }
    }

    private void writeIndex() {
        Json.writeFile(root.resolve(INDEX_FILE), index);
    }

    private void writeHistory(String packageId, List<String> history) {
        Json.writeFile(root.resolve(packageId).resolve(HISTORY_FILE), history);
    }

    private static void writePackage(Path file, SamplePackage pkg) {
        Json.writeFile(file, pkg);
    }

    private static SamplePackage readPackage(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("样本包文件不存在: " + file);
        }
        return Json.readFile(file, SamplePackage.class);
    }
}
