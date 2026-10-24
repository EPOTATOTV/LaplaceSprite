package io.laplace.sprite.game;

import java.time.Instant;
import java.util.Objects;

/**
 * 版本清单里的一条版本记录（对应 {@code version_manifest_v2.json} 的 {@code versions[]}）。
 *
 * @param id              版本 id，如 {@code 26.3}、{@code 26.4-snapshot-5}
 * @param type            release / snapshot / old_beta / old_alpha
 * @param manifestUrl     该版本 JSON 的地址
 * @param sha1            版本 JSON 的 SHA-1
 * @param releaseTime     发布时间
 * @param complianceLevel 合规等级，旧版本为 0
 * @param javaVersion     要求的 Java 大版本；清单未给出时为 0
 */
public record GameVersion(String id, String type, String manifestUrl, String sha1,
                          Instant releaseTime, int complianceLevel, int javaVersion) {

    public GameVersion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(manifestUrl, "manifestUrl");
        Objects.requireNonNull(sha1, "sha1");
        Objects.requireNonNull(releaseTime, "releaseTime");
    }
}
