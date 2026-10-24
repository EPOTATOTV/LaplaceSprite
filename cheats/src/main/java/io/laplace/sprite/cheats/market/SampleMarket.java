package io.laplace.sprite.cheats.market;

import com.fasterxml.jackson.core.type.TypeReference;
import io.laplace.sprite.cheats.SampleSpec;
import io.laplace.sprite.core.io.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 本地样本市场（升级实施文档 L-08 / v2 L2-07）：声明式分发包的安装、版本历史、回滚与治理。
 *
 * <p>目录布局：{@code <root>/index.json} 记录每个包当前版本，{@code <root>/<id>/history.json}
 * 记录安装顺序，{@code <root>/<id>/<version>.json} 是签名包本体，{@code <root>/revoked.json}
 * 是吊销清单。安装顺序固定为签名校验 → 沙箱校验 → 吊销检查 → 落盘；任一步失败都不写文件。
 * 回滚只移动指针，旧版本文件保留，便于再次核对或重新安装。</p>
 */
public final class SampleMarket {

    public static final String INDEX_FILE = "index.json";
    public static final String HISTORY_FILE = "history.json";
    public static final String REVOCATION_FILE = "revoked.json";

    /**
     * 一次安装 / 回滚记录，可直接写入证据链。
     *
     * @param packageId     包 id
     * @param version       版本
     * @param publisher     发布者
     * @param action        install / rollback
     * @param at            发生时间
     * @param sampleIds     该版本的样本 id
     * @param trustScore    来源可信度
     * @param sourceRef     来源引用
     * @param sandboxChecked 是否通过安装前沙箱校验
     */
    public record InstallRecord(String packageId, String version, String publisher, String action,
                                Instant at, List<String> sampleIds, double trustScore, String sourceRef,
                                boolean sandboxChecked) {

        public InstallRecord {
            sampleIds = sampleIds == null ? List.of() : List.copyOf(sampleIds);
            sourceRef = sourceRef == null ? "" : sourceRef;
        }

        public String evidencePayload() {
            return Json.write(this);
        }
    }

    /**
     * 一条吊销记录。
     *
     * @param packageId 包 id
     * @param version   版本
     * @param reason    吊销原因
     * @param revokedAt 吊销时间
     */
    public record Revocation(String packageId, String version, String reason, Instant revokedAt) {

        public Revocation {
            reason = reason == null ? "" : reason;
        }
    }

    /**
     * 搜索结果。
     *
     * @param pkg       包
     * @param relevance 文本相关度
     * @param revoked   当前版本是否已吊销
     */
    public record SearchHit(SamplePackage pkg, double relevance, boolean revoked) {

        public double trustScore() {
            return pkg.trustScore();
        }
    }

    private final Path root;
    private final byte[] key;
    private final Map<String, String> index = new LinkedHashMap<>();
    private final Map<String, Revocation> revocations = new LinkedHashMap<>();

    public SampleMarket(Path root, byte[] key) {
        this.root = root;
        this.key = key.clone();
        index.putAll(readIndex());
        readRevocations().forEach(entry -> revocations.put(revocationKey(entry.packageId(), entry.version()), entry));
    }

    /**
     * 安装一个包；签名、沙箱、吊销任一失败都直接拒绝，不落任何文件。
     *
     * @return 安装记录
     */
    public synchronized InstallRecord install(SamplePackage pkg) {
        if (!SamplePackageSigner.verify(pkg, key)) {
            throw new SecurityException("样本包签名校验失败: " + pkg.id() + "@" + pkg.version());
        }
        SampleSandbox.Check sandbox = SampleSandbox.validate(pkg);
        if (!sandbox.ok()) {
            throw new IllegalArgumentException("样本包未通过沙箱校验: " + String.join("；", sandbox.issues()));
        }
        if (isRevoked(pkg.id(), pkg.version())) {
            throw new SecurityException("样本包版本已被吊销: " + pkg.id() + "@" + pkg.version());
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
        return record(pkg, "install", true);
    }

    /**
     * 回滚到上一个安装版本。历史记录保留，游标前移；没有上一个版本或目标版本已吊销时拒绝。
     */
    public synchronized InstallRecord rollback(String packageId) {
        List<String> history = history(packageId);
        String current = index.get(packageId);
        int currentIndex = current == null ? -1 : history.indexOf(current);
        if (currentIndex <= 0) {
            throw new IllegalStateException("包 " + packageId + " 没有可回滚的历史版本");
        }
        String previous = history.get(currentIndex - 1);
        if (isRevoked(packageId, previous)) {
            throw new IllegalStateException("包 " + packageId + "@" + previous + " 已被吊销，不能回滚");
        }
        SamplePackage pkg = readPackage(root.resolve(packageId).resolve(previous + ".json"));
        index.put(packageId, previous);
        writeIndex();
        return record(pkg, "rollback", false);
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

    /** 已安装包里的全部样本，供 {@code CheatCatalog} 合并。 */
    public synchronized List<SampleSpec> installedSamples() {
        return all().stream().flatMap(pkg -> pkg.samples().stream()).toList();
    }

    public boolean verify(SamplePackage pkg) {
        return SamplePackageSigner.verify(pkg, key);
    }

    /**
     * 按关键词搜索当前已安装包。
     *
     * <p>空关键词返回全部，按可信度倒序；非空时依次匹配包 id（完全匹配 &gt; 前缀 &gt; 包含）、
     * 发布者与样本 id。相关度相同时可信度高的排前面。</p>
     */
    public synchronized List<SearchHit> search(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<SearchHit> hits = new ArrayList<>();
        for (SamplePackage pkg : all()) {
            double relevance = relevance(pkg, needle);
            if (needle.isEmpty() || relevance > 0) {
                hits.add(new SearchHit(pkg, relevance, isRevoked(pkg.id(), pkg.version())));
            }
        }
        hits.sort(Comparator.comparingDouble(SearchHit::relevance).reversed()
                .thenComparing(Comparator.comparingDouble(SearchHit::trustScore).reversed())
                .thenComparing(hit -> hit.pkg().id()));
        return List.copyOf(hits);
    }

    /** 吊销某个包的版本；重复吊销以最后一次原因为准。 */
    public synchronized Revocation revoke(String packageId, String version, String reason) {
        if (packageId == null || packageId.isBlank() || version == null || version.isBlank()) {
            throw new IllegalArgumentException("吊销需要包 id 与版本");
        }
        Revocation revocation = new Revocation(packageId, version, reason, Instant.now());
        revocations.put(revocationKey(packageId, version), revocation);
        writeRevocations();
        return revocation;
    }

    public synchronized boolean isRevoked(String packageId, String version) {
        return revocations.containsKey(revocationKey(packageId, version));
    }

    public synchronized List<Revocation> revocations() {
        return List.copyOf(revocations.values());
    }

    public Path root() {
        return root;
    }

    private InstallRecord record(SamplePackage pkg, String action, boolean sandboxChecked) {
        return new InstallRecord(pkg.id(), pkg.version(), pkg.publisher(), action, Instant.now(),
                pkg.sampleIds(), pkg.trustScore(), pkg.sourceRef(), sandboxChecked);
    }

    private static double relevance(SamplePackage pkg, String needle) {
        if (needle.isEmpty()) {
            return 0;
        }
        String id = pkg.id().toLowerCase(Locale.ROOT);
        double score = 0;
        if (id.equals(needle)) {
            score += 3;
        } else if (id.startsWith(needle)) {
            score += 2;
        } else if (id.contains(needle)) {
            score += 1;
        }
        if (pkg.publisher().toLowerCase(Locale.ROOT).contains(needle)) {
            score += 1;
        }
        for (SampleSpec sample : pkg.samples()) {
            if (sample.id().toLowerCase(Locale.ROOT).contains(needle)
                    || (sample.name() != null && sample.name().toLowerCase(Locale.ROOT).contains(needle))) {
                score += 0.5;
            }
        }
        return score;
    }

    private static String revocationKey(String packageId, String version) {
        return packageId + "@" + version;
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

    private List<Revocation> readRevocations() {
        Path file = root.resolve(REVOCATION_FILE);
        if (!Files.isRegularFile(file)) {
            return List.of();
        }
        return List.of(Json.readFile(file, Revocation[].class));
    }

    private void writeIndex() {
        Json.writeFile(root.resolve(INDEX_FILE), index);
    }

    private void writeRevocations() {
        Json.writeFile(root.resolve(REVOCATION_FILE), List.copyOf(revocations.values()));
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
