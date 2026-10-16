package io.laplace.sprite.report;

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
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * 证据库（4.6 EvidenceVault）：本地落盘 + 索引持久化，误报 / 检出样本自动归档，重启保留。
 *
 * <p>每条归档同时生成一环 HMAC 链（{@link EvidenceChainLink}），链与索引一起落盘，重启后仍可校验。
 * 密钥来源与它能给出的保证见 {@link #EvidenceVault(Path)}。</p>
 */
public final class EvidenceVault {

    private static final String INDEX_FILE = "index.json";
    private static final String CHAIN_FILE = "chain.json";
    private static final String KEY_FILE = "hmac.key";

    /** 密钥环境变量名：密钥由 vault 之外保管时用它注入。 */
    public static final String HMAC_KEY_ENV = "LAPLACE_EVIDENCE_HMAC_KEY";

    private static final int KEY_BYTES = 32;

    private final Path root;
    private final Path indexPath;
    private final Path chainPath;
    private final byte[] hmacKey;
    private final List<EvidenceRecord> records = new ArrayList<>();
    private final List<EvidenceChainLink> chain = new ArrayList<>();

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
        this.hmacKey = hmacKey.clone();
        records.addAll(load());
        chain.addAll(loadChain());
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
