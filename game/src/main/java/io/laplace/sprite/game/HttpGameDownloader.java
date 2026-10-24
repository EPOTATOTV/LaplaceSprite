package io.laplace.sprite.game;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;

/**
 * HTTP 下载器：先写 {@code <target>.part}，校验通过后原子改名；首次失败后最多重试三次。
 *
 * <p>校验失败会删掉临时文件重来，全部尝试失败才抛异常，消息里同时给期望值与实际值。</p>
 */
public final class HttpGameDownloader implements GameDownloader {

    private static final int MAX_RETRIES = 3;
    private static final String PART_SUFFIX = ".part";

    /** 可注入的字节来源；测试用它替代真实网络。 */
    @FunctionalInterface
    interface ArtifactSource {
        void writeTo(URI uri, Path destination) throws IOException;
    }

    private final ArtifactSource source;
    private final int maxAttempts;

    public HttpGameDownloader() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.source = (uri, destination) -> downloadHttp(client, uri, destination);
        this.maxAttempts = MAX_RETRIES + 1;
    }

    HttpGameDownloader(ArtifactSource source, int maxAttempts) {
        this.source = Objects.requireNonNull(source, "source");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("尝试次数至少为 1: " + maxAttempts);
        }
        this.maxAttempts = maxAttempts;
    }

    @Override
    public DownloadResult download(URI uri, Path target, String expectedHash,
                                   HashAlgorithm algorithm) throws IOException {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(algorithm, "algorithm");
        if (expectedHash == null || expectedHash.isBlank()) {
            throw new IllegalArgumentException("期望哈希不能为空: " + uri);
        }

        Path destination = target.toAbsolutePath().normalize();
        Path parent = destination.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path part = destination.resolveSibling(destination.getFileName() + PART_SUFFIX);

        IOException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            Files.deleteIfExists(part);
            try {
                source.writeTo(uri, part);
            } catch (IOException ex) {
                lastFailure = new IOException("下载 " + uri + " 第 " + attempt + " 次失败", ex);
                Files.deleteIfExists(part);
                continue;
            }

            Digests digests;
            try {
                digests = digest(part);
            } catch (IOException ex) {
                lastFailure = new IOException("读取临时文件失败: " + part, ex);
                Files.deleteIfExists(part);
                continue;
            }

            String actual = algorithm == HashAlgorithm.SHA1 ? digests.sha1() : digests.sha256();
            if (!actual.equalsIgnoreCase(expectedHash)) {
                Files.deleteIfExists(part);
                lastFailure = new IOException("哈希校验失败（第 " + attempt + " 次）: 期望 "
                        + algorithm.name() + "=" + expectedHash + "，实际=" + actual + "（" + uri + "）");
                continue;
            }

            try {
                moveIntoPlace(part, destination);
            } catch (IOException ex) {
                Files.deleteIfExists(part);
                throw new IOException("下载完成但移动到目标失败: " + destination, ex);
            }
            return new DownloadResult(destination, Files.size(destination), digests.sha1(),
                    digests.sha256(), attempt, true);
        }

        Files.deleteIfExists(part);
        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new IOException("下载失败: " + uri);
    }

    private static void moveIntoPlace(Path part, Path destination) throws IOException {
        try {
            Files.move(part, destination, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException | FileAlreadyExistsException ex) {
            Files.move(part, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Digests digest(Path file) throws IOException {
        MessageDigest sha1 = digestInstance(HashAlgorithm.SHA1);
        MessageDigest sha256 = digestInstance(HashAlgorithm.SHA256);
        byte[] buffer = new byte[8192];
        try (InputStream in = Files.newInputStream(file)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                sha1.update(buffer, 0, read);
                sha256.update(buffer, 0, read);
            }
        }
        HexFormat hex = HexFormat.of();
        return new Digests(hex.formatHex(sha1.digest()), hex.formatHex(sha256.digest()));
    }

    private static MessageDigest digestInstance(HashAlgorithm algorithm) {
        try {
            return MessageDigest.getInstance(algorithm.messageDigestName());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JDK 缺少哈希算法 " + algorithm.messageDigestName(), ex);
        }
    }

    private static void downloadHttp(HttpClient client, URI uri, Path destination) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("User-Agent", "laplace-sprite")
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response =
                    client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                response.body().close();
                throw new IOException("下载 " + uri + " 失败：HTTP " + response.statusCode());
            }
            try (InputStream body = response.body()) {
                Files.copy(body, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("下载 " + uri + " 被中断", ex);
        }
    }

    private record Digests(String sha1, String sha256) {
    }
}
