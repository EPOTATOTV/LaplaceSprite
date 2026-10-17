package io.laplace.sprite.replay;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 世界快照四元组标签（15.10）：世界种子、MC 版本、mod / 数据包清单 hash、录制场景 id。
 *
 * <p>{@code contentHash} 对清单排序后再算，因此清单条目或 map 键的书写顺序不影响结果。
 * 清单只做去空白与排序，不解析 mod 版本语义。</p>
 *
 * @param worldSeed   世界种子
 * @param mcVersion   Minecraft 版本
 * @param contentHash mod / 数据包清单的 SHA-256（小写十六进制）
 * @param sceneId     录制场景 id，与 .mcpr 双向绑定
 */
public record WorldSnapshotTag(String worldSeed, String mcVersion, String contentHash, String sceneId) {

    public WorldSnapshotTag {
        worldSeed = requireText(worldSeed, "worldSeed");
        mcVersion = requireText(mcVersion, "mcVersion");
        contentHash = requireText(contentHash, "contentHash");
        sceneId = requireText(sceneId, "sceneId");
        if (!contentHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("contentHash 必须是 64 位小写十六进制: " + contentHash);
        }
    }

    public static WorldSnapshotTag of(String worldSeed, String mcVersion, Collection<String> manifest,
                                      String sceneId) {
        return new WorldSnapshotTag(worldSeed, mcVersion, contentHash(manifest), sceneId);
    }

    public static WorldSnapshotTag of(String worldSeed, String mcVersion, Map<String, String> manifest,
                                      String sceneId) {
        return new WorldSnapshotTag(worldSeed, mcVersion, contentHash(manifest), sceneId);
    }

    /** 条目清单的 hash：去空行、去首尾空白、排序后按行拼接。 */
    public static String contentHash(Collection<String> manifestEntries) {
        Objects.requireNonNull(manifestEntries, "manifestEntries");
        List<String> normalized = new ArrayList<>();
        for (String entry : manifestEntries) {
            if (entry == null) {
                continue;
            }
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        normalized.sort(String::compareTo);
        return sha256(String.join("\n", normalized));
    }

    /** 键值清单的 hash：按键排序，写成 {@code key=value} 行。 */
    public static String contentHash(Map<String, String> manifest) {
        Objects.requireNonNull(manifest, "manifest");
        List<String> lines = new ArrayList<>();
        manifest.forEach((key, value) -> lines.add(key.trim() + "=" + (value == null ? "" : value.trim())));
        lines.sort(String::compareTo);
        return sha256(String.join("\n", lines));
    }

    /** 文件系统安全的目录名；同一四元组永远得到同一个名字。 */
    public String label() {
        return "snap-" + safe(worldSeed) + "-" + safe(mcVersion) + "-" + contentHash.substring(0, 12)
                + "-" + safe(sceneId);
    }

    private static String sha256(String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", ex);
        }
    }

    private static String safe(String value) {
        String replaced = value.trim().replaceAll("[^A-Za-z0-9._-]", "_");
        while (replaced.startsWith(".")) {
            replaced = "_" + replaced.substring(1);
        }
        return replaced.isEmpty() ? "unknown" : replaced;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
        return value;
    }
}
