package io.laplace.sprite.game;

import java.io.IOException;
import java.net.URI;

/**
 * 版本清单与版本 JSON 的取数入口。测试里换成固定字符串，生产里用 HTTP。
 */
@FunctionalInterface
public interface ManifestFetcher {

    String fetch(URI uri) throws IOException;
}
