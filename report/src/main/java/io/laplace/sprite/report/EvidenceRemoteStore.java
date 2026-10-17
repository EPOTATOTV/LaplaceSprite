package io.laplace.sprite.report;

import java.util.List;

/**
 * 证据远端存储（4.6 EvidenceVault「本地 + 可上云存储」的云侧）。
 *
 * <p>本地 vault 始终是事实来源，远端只是副本：上传失败或远端不可用都不得影响本地归档与证据链校验。
 * 远端引用由实现决定，上传成功后由 {@link EvidenceVault} 记账。</p>
 */
public interface EvidenceRemoteStore {

    /** 上传一份证据，返回远端引用；失败时抛异常，调用方按条记录失败而不是静默吞掉。 */
    String put(String evidenceId, String relativePath, byte[] payload);

    /** 按远端引用取回字节。 */
    byte[] get(String remoteRef);

    boolean exists(String remoteRef);

    /** 远端对象清单；做不到全量清点时应写明范围（如「本会话内上传的对象」）。 */
    List<RemoteObject> list();

    /** 人类可读说明；未配置时必须明确写出「未配置、未上传任何证据」。 */
    String describe();

    /** 是否已配置到可上传。本地镜像恒为 true，HTTP 对象存储看环境变量是否齐全。 */
    default boolean configured() {
        return true;
    }

    /**
     * 统一的引用形状：反斜杠转正斜杠、去掉前导斜杠，并拒绝 {@code ..} 以免逃出镜像 / 桶前缀。
     */
    static String normalizeKey(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("远端引用不能为空");
        }
        String key = relativePath.replace('\\', '/');
        while (key.startsWith("/")) {
            key = key.substring(1);
        }
        if (key.contains("..")) {
            throw new IllegalArgumentException("非法远端引用: " + relativePath);
        }
        return key;
    }
}
