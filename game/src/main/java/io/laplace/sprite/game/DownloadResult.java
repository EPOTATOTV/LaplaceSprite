package io.laplace.sprite.game;

import java.nio.file.Path;

/**
 * 一次下载的结果。
 *
 * @param path     最终文件
 * @param bytes    字节数
 * @param sha1     实际 SHA-1
 * @param sha256   实际 SHA-256
 * @param attempts 实际尝试次数
 * @param verified 是否通过期望哈希校验
 */
public record DownloadResult(Path path, long bytes, String sha1, String sha256, int attempts,
                             boolean verified) {
}
