package io.laplace.sprite.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 下载校验与重试，全部走注入的字节源，不访问网络。 */
class HttpGameDownloaderTest {

    private static final URI SOURCE = URI.create("https://example.invalid/client.jar");

    @TempDir
    Path temp;

    @Test
    void retriesAfterHashMismatchThenSucceeds() throws IOException, NoSuchAlgorithmException {
        AtomicInteger calls = new AtomicInteger();
        HttpGameDownloader.ArtifactSource source = (uri, destination) -> {
            int call = calls.incrementAndGet();
            Files.writeString(destination, call < 4 ? "wrong" : "right", StandardCharsets.UTF_8);
        };
        Path target = temp.resolve("client.jar");
        String expectedSha1 = hash(HashAlgorithm.SHA1, "right");

        DownloadResult result = new HttpGameDownloader(source, 4)
                .download(SOURCE, target, expectedSha1, HashAlgorithm.SHA1);

        assertEquals(4, calls.get());
        assertEquals(4, result.attempts());
        assertTrue(result.verified());
        assertEquals(5, result.bytes());
        assertEquals(expectedSha1, result.sha1());
        assertEquals(hash(HashAlgorithm.SHA256, "right"), result.sha256());
        assertEquals("right", Files.readString(target, StandardCharsets.UTF_8));
        assertFalse(Files.exists(temp.resolve("client.jar.part")), "临时文件不能残留");
    }

    @Test
    void failsWithExpectedAndActualHashAfterAllAttempts() throws NoSuchAlgorithmException {
        AtomicInteger calls = new AtomicInteger();
        HttpGameDownloader.ArtifactSource source = (uri, destination) -> {
            calls.incrementAndGet();
            Files.writeString(destination, "wrong", StandardCharsets.UTF_8);
        };
        Path target = temp.resolve("server.jar");
        String expected = hash(HashAlgorithm.SHA1, "right");
        String actual = hash(HashAlgorithm.SHA1, "wrong");

        IOException ex = assertThrows(IOException.class,
                () -> new HttpGameDownloader(source, 4)
                        .download(SOURCE, target, expected, HashAlgorithm.SHA1));

        assertEquals(4, calls.get());
        assertTrue(ex.getMessage().contains(expected), ex.getMessage());
        assertTrue(ex.getMessage().contains(actual), ex.getMessage());
        assertFalse(Files.exists(target), "校验失败不能留下目标文件");
        assertFalse(Files.exists(temp.resolve("server.jar.part")), "临时文件不能残留");
    }

    @Test
    void acceptsSha256ExpectationAndStillReportsBothDigests()
            throws IOException, NoSuchAlgorithmException {
        String payload = "payload";
        HttpGameDownloader.ArtifactSource source = (uri, destination) ->
                Files.writeString(destination, payload, StandardCharsets.UTF_8);
        Path target = temp.resolve("nested").resolve("client.jar");
        String expected = hash(HashAlgorithm.SHA256, payload);

        DownloadResult result = new HttpGameDownloader(source, 3)
                .download(SOURCE, target, expected, HashAlgorithm.SHA256);

        assertTrue(result.verified());
        assertEquals(expected, result.sha256());
        assertEquals(hash(HashAlgorithm.SHA1, payload), result.sha1());
        assertTrue(Files.isRegularFile(target));
    }

    private static String hash(HashAlgorithm algorithm, String content) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(algorithm.messageDigestName());
        return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
    }
}
