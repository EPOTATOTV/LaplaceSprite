package io.laplace.sprite.cheats.market;

import io.laplace.sprite.cheats.SampleSpec;
import io.laplace.sprite.core.io.Json;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * 样本包签名（L-08）：HMAC-SHA256 覆盖 id、版本、发布者与按 id 排序后的样本 JSON。
 *
 * <p>签名算法与证据链同源，避免为分发再引一套密钥体系；密钥由调用方保管，
 * 市场目录里不落任何密钥文件。</p>
 */
public final class SamplePackageSigner {

    private static final String ALGORITHM = "HmacSHA256";

    private SamplePackageSigner() {
    }

    public static SamplePackage sign(SamplePackage pkg, byte[] key) {
        return new SamplePackage(pkg.id(), pkg.version(), pkg.publisher(), pkg.samples(),
                signature(pkg.unsigned(), key));
    }

    public static boolean verify(SamplePackage pkg, byte[] key) {
        return pkg.signed() && pkg.signature().equalsIgnoreCase(signature(pkg.unsigned(), key));
    }

    /** 规范串：四个字段 + 排序后的样本 JSON；两边必须逐字复现。 */
    public static String canonical(SamplePackage pkg) {
        List<SampleSpec> sorted = pkg.samples().stream()
                .sorted(Comparator.comparing(SampleSpec::id))
                .toList();
        return pkg.id() + "\n" + pkg.version() + "\n" + pkg.publisher() + "\n" + Json.write(sorted);
    }

    public static String signature(SamplePackage pkg, byte[] key) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(canonical(pkg).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ALGORITHM + " 不可用", ex);
        }
    }
}
