package io.laplace.sprite.datapack;

import java.util.Objects;

/**
 * pack.mcmeta 解析的两种结局：拿到元数据，或拿到结构化错误。
 *
 * @param meta  解析成功时的元数据
 * @param error 解析失败时的错误
 */
public record PackMetaResult(PackMeta meta, PackMetaError error) {

    public PackMetaResult {
        if ((meta == null) == (error == null)) {
            throw new IllegalArgumentException("PackMetaResult 必须恰好持有 meta 或 error 之一");
        }
    }

    public static PackMetaResult ok(PackMeta meta) {
        return new PackMetaResult(Objects.requireNonNull(meta, "meta"), null);
    }

    public static PackMetaResult failure(PackMetaError error) {
        return new PackMetaResult(null, Objects.requireNonNull(error, "error"));
    }

    public boolean ok() {
        return meta != null;
    }
}
