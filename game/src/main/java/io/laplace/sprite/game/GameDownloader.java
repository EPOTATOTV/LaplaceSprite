package io.laplace.sprite.game;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

/**
 * 产物下载与哈希校验。
 */
@FunctionalInterface
public interface GameDownloader {

    DownloadResult download(URI uri, Path target, String expectedHash, HashAlgorithm algorithm)
            throws IOException;
}
