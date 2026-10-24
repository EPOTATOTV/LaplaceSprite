package io.laplace.sprite.game;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * 版本清单客户端：网络取到的原文先落缓存再解析，离线时只读缓存。
 */
public final class VersionManifestClient {

    public static final URI MANIFEST_URI =
            URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final ManifestFetcher fetcher;
    private final Path cacheFile;

    public VersionManifestClient(ManifestFetcher fetcher, Path cacheFile) {
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
        this.cacheFile = Objects.requireNonNull(cacheFile, "cacheFile").toAbsolutePath().normalize();
    }

    public VersionManifestClient(Path cacheFile) {
        this(httpFetcher(), cacheFile);
    }

    /**
     * 走网络取清单，先写缓存再解析。失败不吞：网络异常原样抛出，JSON 问题抛
     * {@link IllegalArgumentException}。
     */
    public VersionManifest fetch() throws IOException {
        String content = fetcher.fetch(MANIFEST_URI);
        if (cacheFile.getParent() != null) {
            Files.createDirectories(cacheFile.getParent());
        }
        Files.writeString(cacheFile, content, StandardCharsets.UTF_8);
        return VersionManifest.parse(content);
    }

    /** 只读缓存；缓存不存在返回空。 */
    public Optional<VersionManifest> loadCached() throws IOException {
        if (!Files.isRegularFile(cacheFile)) {
            return Optional.empty();
        }
        return Optional.of(VersionManifest.parse(Files.readString(cacheFile, StandardCharsets.UTF_8)));
    }

    public Path cacheFile() {
        return cacheFile;
    }

    /** 10 秒超时的 HTTP 取数实现。非 2xx 与中断都转成带地址的 IOException。 */
    public static ManifestFetcher httpFetcher() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        return uri -> {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(TIMEOUT)
                    .header("User-Agent", "laplace-sprite")
                    .GET()
                    .build();
            try {
                HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IOException("请求 " + uri + " 失败：HTTP " + response.statusCode());
                }
                return response.body();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("请求 " + uri + " 被中断", ex);
            }
        };
    }
}
