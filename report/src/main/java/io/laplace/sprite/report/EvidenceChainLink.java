package io.laplace.sprite.report;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 证据链的一环（4.6）：{@code index | timestamp | payloadHash | prevMac} 参与 HMAC 计算，mac 是结果。
 *
 * @param index       链内序号，从 0 起
 * @param timestamp   归档时间，与记录一致并参与摘要
 * @param payloadHash 归档内容字节的 SHA-256（hex）
 * @param prevMac     上一环的 mac；首环取 {@link #GENESIS_PREV_MAC}
 * @param mac         HMAC-SHA256(canonical) 的 hex
 * @param evidenceId  对应证据记录 id
 */
public record EvidenceChainLink(int index, Instant timestamp, String payloadHash, String prevMac, String mac,
                                String evidenceId) {

    /**
     * 创世前驱：首环没有上一环，用 64 个 0 占位而不是空串，字段个数与后续环保持一致。
     */
    public static final String GENESIS_PREV_MAC = "0".repeat(64);

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String DIGEST_ALGORITHM = "SHA-256";
    /** 4.6 规定的字段顺序与分隔符，校验方必须逐字复现。 */
    private static final String SEPARATOR = " | ";

    /** 拼接参与 HMAC 的规范串。 */
    public static String canonical(int index, Instant timestamp, String payloadHash, String prevMac) {
        return index + SEPARATOR + timestamp + SEPARATOR + payloadHash + SEPARATOR + prevMac;
    }

    public static String payloadHash(byte[] payload) {
        return HexFormat.of().formatHex(digest(payload));
    }

    public static String mac(byte[] key, int index, Instant timestamp, String payloadHash, String prevMac) {
        try {
            Mac hmac = Mac.getInstance(HMAC_ALGORITHM);
            hmac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            byte[] raw = hmac.doFinal(canonical(index, timestamp, payloadHash, prevMac)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(HMAC_ALGORITHM + " 不可用", ex);
        }
    }

    private static byte[] digest(byte[] payload) {
        try {
            return MessageDigest.getInstance(DIGEST_ALGORITHM).digest(payload);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(DIGEST_ALGORITHM + " 不可用", ex);
        }
    }
}
