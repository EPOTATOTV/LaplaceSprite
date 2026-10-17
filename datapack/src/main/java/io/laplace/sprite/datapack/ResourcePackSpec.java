package io.laplace.sprite.datapack;

import java.util.Locale;

/**
 * 资源包声明（15.8）。
 *
 * @param url      资源包 URL 或本地路径（目录 / zip）
 * @param packMeta 调用方已解析好的元数据；为空时由解析器读取内容
 * @param sha1     声明的 SHA-1；为空表示不校验
 * @param required 是否强制客户端接受（对应 server.properties 的 resource-pack-required）
 */
public record ResourcePackSpec(String url, PackMeta packMeta, String sha1, boolean required) {

    public ResourcePackSpec {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("资源包 URL 不能为空");
        }
        sha1 = sha1 == null ? "" : sha1.trim().toLowerCase(Locale.ROOT);
    }

    /** 是否 http(s) 远端地址；远端内容不在无头环境下载。 */
    public boolean remote() {
        return url.startsWith("http://") || url.startsWith("https://");
    }
}
