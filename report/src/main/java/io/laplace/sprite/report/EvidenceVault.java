package io.laplace.sprite.report;

import com.fasterxml.jackson.core.type.TypeReference;

import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.core.model.EvidenceRecord;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 证据库（4.6 EvidenceVault）：本地落盘 + 索引持久化，误报 / 检出样本自动归档，重启保留；本地之外可再
 * 同步一份到远端存储（本地 + 可上云存储）。
 *
 * <p>每条归档同时生成一环 HMAC 链（{@link EvidenceChainLink}），链与索引一起落盘，重启后仍可校验。
 * 密钥来源与它能给出的保证见 {@link #EvidenceVault(Path)}。</p>
 *
 * <p>远端同步见 {@link #syncRemote}：本地始终是事实来源，上传只写远端副本并把远端引用记进
 * {@code remote-index.json}，绝不改写链或索引；{@link #verifyRemote} 用链上的 payloadHash 核对远端内容。</p>
 */
public final class EvidenceVault {

    private static final String INDEX_FILE = "index.json";
    private static final String CHAIN_FILE = "chain.json";
    private static final String KEY_FILE = "hmac.key";
    /** 远端引用索引：evidenceId → remoteRef，单独一个文件，不动证据链。 */
    private static final String REMOTE_INDEX_FILE = "remote-index.json";

    /** 密钥环境变量名：密钥由 vault 之外保管时用它注入。 */
    public static final String HMAC_KEY_ENV = "LAPLACE_EVIDENCE_HMAC_KEY";

    private static final int KEY_BYTES = 32;

    private final Path root;
    private final Path indexPath;
    private final Path chainPath;
    private final Path remoteIndexPath;
    private final byte[] hmacKey;
    private final List<EvidenceRecord> records = new ArrayList<>();
    private final List<EvidenceChainLink> chain = new ArrayList<>();
    private final Map<String, String> remoteRefs = new LinkedHashMap<>();

    /**
     * 密钥先取环境变量 {@value #HMAC_KEY_ENV}，没有就在 {@code root/hmac.key} 生成并复用。
     *
     * <p>这条路径把密钥与数据放在一起，因此只能证明「归档之后内容没有被单独改写」：能同时改写
     * {@code chain.json} 与密钥文件的攻击者可以重算整条链。要拿到真正的防删改保证，密钥必须放在 vault
     * 之外保管（改用环境变量或 {@link #EvidenceVault(Path, byte[])}），vault 目录本身只当只读副本。</p>
     */
    public EvidenceVault(Path root) {
        this(root, resolveKey(root));
    }

    /** 显式给密钥：保管责任在调用方，vault 不落任何密钥文件。 */
    public EvidenceVault(Path root, byte[] hmacKey) {
        this.root = root;
        this.indexPath = root.resolve(INDEX_FILE);
        this.chainPath = root.resolve(CHAIN_FILE);
        this.remoteIndexPath = root.resolve(REMOTE_INDEX_FILE);
        this.hmacKey = hmacKey.clone();
        records.addAll(load());
        chain.addAll(loadChain());
        remoteRefs.putAll(loadRemoteIndex());
    }

    /** 归档一条证据：blob 落到 {@code root/<runId>/<kind>-<id>.txt}，写入索引并串上链尾。 */
    public EvidenceRecord archive(String runId, String kind, String content, String description) {
        String id = "ev-" + String.format("%05d", records.size() + 1);
        Path blob = root.resolve(runId).resolve(kind + "-" + id + ".txt");
        byte[] payload = content.getBytes(StandardCharsets.UTF_8);
        try {
            Files.createDirectories(blob.getParent());
            Files.write(blob, payload);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        EvidenceRecord record = new EvidenceRecord(id, runId, kind, blob.toString(), description, Instant.now());
        records.add(record);
        chain.add(linkFor(record, payload));
        persist();
        return record;
    }

    public List<EvidenceRecord> byRun(String runId) {
        return records.stream().filter(r -> r.runId().equals(runId)).toList();
    }

    public List<EvidenceRecord> all() {
        return List.copyOf(records);
    }

    public Optional<EvidenceRecord> byId(String id) {
        return records.stream().filter(r -> r.id().equals(id)).findFirst();
    }

    public int size() {
        return records.size();
    }

    /** 证据链快照（顺序即归档顺序）。 */
    public List<EvidenceChainLink> chain() {
        return List.copyOf(chain);
    }

    /** 链摘要 JSON，供报告与命令行引用。 */
    public String chainJson() {
        return Json.write(chain);
    }

    /** 链摘要 Markdown 表：逐环列出序号、证据、摘要与逐环校验结论。 */
    public String chainMarkdown() {
        EvidenceChainVerifier.ChainVerification verification = new EvidenceChainVerifier(this).verify();
        StringBuilder md = new StringBuilder();
        md.append("| 序号 | 证据 | 归档时间 | payloadHash | prevMac | mac |\n");
        md.append("| ---: | --- | --- | --- | --- | --- |\n");
        for (EvidenceChainLink link : chain) {
            md.append("| ").append(link.index())
                    .append(" | ").append(link.evidenceId())
                    .append(" | ").append(link.timestamp())
                    .append(" | ").append(shortHash(link.payloadHash()))
                    .append(" | ").append(shortHash(link.prevMac()))
                    .append(" | ").append(shortHash(link.mac()))
                    .append(" |\n");
        }
        md.append('\n').append("校验：").append(verification.valid() ? "通过" : "不通过")
                .append("（已校验 ").append(verification.checkedLinks()).append(" 环");
        if (!verification.valid()) {
            md.append("，首个断点 ").append(verification.firstBrokenIndex());
        }
        md.append("）· ").append(verification.reason()).append('\n');
        return md.toString();
    }

    /** 校验方取的密钥副本。 */
    byte[] hmacKey() {
        return hmacKey.clone();
    }

    /** 该证据的远端引用；尚未同步时为空。 */
    public Optional<String> remoteRef(String evidenceId) {
        return Optional.ofNullable(remoteRefs.get(evidenceId));
    }

    /** 已有远端副本的证据条数。 */
    public int remoteCount() {
        return remoteRefs.size();
    }

    /**
     * 把尚无远端引用的本地证据上传到远端存储，逐条记账并写入远端索引。本地索引与证据链在任何情况下都不改。
     *
     * <p>存储未配置时不做任何上传，失败如实计入 {@link RemoteSyncReport}，不假装成功。</p>
     */
    public RemoteSyncReport syncRemote(EvidenceRemoteStore store) {
        int pending = 0;
        for (EvidenceRecord record : records) {
            if (!remoteRefs.containsKey(record.id())) {
                pending++;
            }
        }
        if (!store.configured()) {
            return new RemoteSyncReport(records.size(), 0, records.size() - pending, pending,
                    List.of("对象存储未配置（" + store.describe() + "），本轮未上传任何证据"));
        }
        int uploaded = 0;
        int failed = 0;
        List<String> failures = new ArrayList<>();
        for (EvidenceRecord record : records) {
            if (remoteRefs.containsKey(record.id())) {
                continue;
            }
            try {
                byte[] payload = Files.readAllBytes(Path.of(record.path()));
                remoteRefs.put(record.id(), store.put(record.id(), relativePath(record.path()), payload));
                uploaded++;
            } catch (IOException | RuntimeException ex) {
                failed++;
                failures.add("证据 %s 上传失败：%s".formatted(record.id(), ex.getMessage()));
            }
        }
        if (uploaded > 0) {
            persistRemoteIndex();
        }
        return new RemoteSyncReport(records.size(), uploaded, records.size() - pending, failed, failures);
    }

    /**
     * 核对远端副本：逐条取回远端字节、重算 SHA-256，与链上 payloadHash 比对。不符或缺失都记为失败。
     *
     * <p>比对基准是密不可分的证据链，而不是远端自报的元数据，因此远端被换过内容一定查得出来。</p>
     */
    public RemoteVerification verifyRemote(EvidenceRemoteStore store) {
        if (!store.configured()) {
            return new RemoteVerification(0, 0, 0, 0,
                    List.of("对象存储未配置（" + store.describe() + "），无法校验远端副本"));
        }
        Map<String, String> chainHashes = new HashMap<>();
        for (EvidenceChainLink link : chain) {
            chainHashes.put(link.evidenceId(), link.payloadHash());
        }
        int checked = 0;
        int matched = 0;
        int mismatched = 0;
        int missing = 0;
        List<String> failures = new ArrayList<>();
        for (EvidenceRecord record : records) {
            String ref = remoteRefs.get(record.id());
            if (ref == null) {
                continue;
            }
            checked++;
            String expected = chainHashes.get(record.id());
            if (expected == null) {
                mismatched++;
                failures.add("证据 %s 无链上 payloadHash，无法校验远端副本".formatted(record.id()));
                continue;
            }
            try {
                if (!store.exists(ref)) {
                    missing++;
                    failures.add("证据 %s 远端对象 %s 缺失".formatted(record.id(), ref));
                    continue;
                }
                String actual = EvidenceChainLink.payloadHash(store.get(ref));
                if (actual.equals(expected)) {
                    matched++;
                } else {
                    mismatched++;
                    failures.add("证据 %s 远端内容与链上 payloadHash 不符（远端 %s / 链上 %s）"
                            .formatted(record.id(), shortHash(actual), shortHash(expected)));
                }
            } catch (RuntimeException ex) {
                mismatched++;
                failures.add("证据 %s 远端校验失败：%s".formatted(record.id(), ex.getMessage()));
            }
        }
        return new RemoteVerification(checked, matched, mismatched, missing, failures);
    }

    /** 证据 blob 相对 vault 根的路径，用作远端对象键；不在根下时退化为文件名。 */
    private String relativePath(String blobPath) {
        Path path = Path.of(blobPath);
        if (path.startsWith(root)) {
            return root.relativize(path).toString().replace('\\', '/');
        }
        Path fileName = path.getFileName();
        return fileName == null ? blobPath : fileName.toString();
    }

    private EvidenceChainLink linkFor(EvidenceRecord record, byte[] payload) {
        int index = chain.size();
        String prevMac = index == 0 ? EvidenceChainLink.GENESIS_PREV_MAC : chain.get(index - 1).mac();
        String payloadHash = EvidenceChainLink.payloadHash(payload);
        String mac = EvidenceChainLink.mac(hmacKey, index, record.createdAt(), payloadHash, prevMac);
        return new EvidenceChainLink(index, record.createdAt(), payloadHash, prevMac, mac, record.id());
    }

    private static String shortHash(String hash) {
        return hash.length() <= 12 ? hash : hash.substring(0, 12) + "…";
    }

    private List<EvidenceRecord> load() {
        if (!Files.exists(indexPath)) {
            return List.of();
        }
        try {
            String content = Files.readString(indexPath, StandardCharsets.UTF_8);
            if (content.isBlank()) {
                return List.of();
            }
            return List.of(Json.read(content, EvidenceRecord[].class));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private List<EvidenceChainLink> loadChain() {
        if (!Files.exists(chainPath)) {
            return List.of();
        }
        try {
            String content = Files.readString(chainPath, StandardCharsets.UTF_8);
            if (content.isBlank()) {
                return List.of();
            }
            return List.of(Json.read(content, EvidenceChainLink[].class));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void persist() {
        Json.writeFile(indexPath, records);
        Json.writeFile(chainPath, chain);
    }

    private Map<String, String> loadRemoteIndex() {
        if (!Files.exists(remoteIndexPath)) {
            return Map.of();
        }
        try {
            String content = Files.readString(remoteIndexPath, StandardCharsets.UTF_8);
            if (content.isBlank()) {
                return Map.of();
            }
            return Json.jsonMapper().readValue(content, new TypeReference<Map<String, String>>() {
            });
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private void persistRemoteIndex() {
        Json.writeFile(remoteIndexPath, remoteRefs);
    }

    private static byte[] resolveKey(Path root) {
        String fromEnv = System.getenv(HMAC_KEY_ENV);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.getBytes(StandardCharsets.UTF_8);
        }
        return loadOrCreateKey(root.resolve(KEY_FILE));
    }

    private static byte[] loadOrCreateKey(Path keyFile) {
        try {
            if (Files.exists(keyFile)) {
                return HexFormat.of().parseHex(Files.readString(keyFile, StandardCharsets.UTF_8).trim());
            }
            byte[] key = new byte[KEY_BYTES];
            new SecureRandom().nextBytes(key);
            if (keyFile.getParent() != null) {
                Files.createDirectories(keyFile.getParent());
            }
            Files.writeString(keyFile, HexFormat.of().formatHex(key), StandardCharsets.UTF_8);
            return key;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
