package io.laplace.sprite.datapack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 资源包解析层冒烟（15.8）：只到 pack.mcmeta，不碰客户端材质。 */
class ResourcePackResolverTest {

    private static final String VALID_META =
            "{\"pack\":{\"pack_format\":46,\"description\":\"smoke resource pack\"}}";

    @TempDir
    Path tempDir;

    private final ResourcePackResolver resolver = new ResourcePackResolver();

    @Test
    void resolvesLocalDirectoryPack() throws IOException {
        Path pack = Files.createDirectories(tempDir.resolve("rp"));
        Files.writeString(pack.resolve("pack.mcmeta"), VALID_META);

        ResourcePackResolver.LoadVerdict verdict = resolver.resolve(
                new ResourcePackSpec(pack.toString(), null, "", true));

        assertTrue(verdict.loadable());
        assertTrue(verdict.metaParsed());
        assertNull(verdict.error());
        assertEquals(46, verdict.meta().packFormat());
        assertTrue(verdict.notes().stream().anyMatch(note -> note.contains("客户端材质不在本轨验证")));
    }

    @Test
    void resolvesZipPack() throws IOException {
        Path zip = tempDir.resolve("rp.zip");
        writeZip(zip, "pack.mcmeta", VALID_META);

        ResourcePackResolver.LoadVerdict verdict = resolver.resolve(
                new ResourcePackSpec(zip.toString(), null, "", true));

        assertTrue(verdict.loadable());
        assertEquals("smoke resource pack", verdict.meta().description());
    }

    @Test
    void malformedPackMetaFailsWithMojiraId() throws IOException {
        Path pack = Files.createDirectories(tempDir.resolve("broken"));
        Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":46,");

        ResourcePackResolver.LoadVerdict verdict = resolver.resolve(
                new ResourcePackSpec(pack.toString(), null, "", true));

        assertFalse(verdict.loadable());
        assertFalse(verdict.metaParsed());
        assertEquals(PackMetaErrorKind.JSON_SYNTAX, verdict.error().kind());
        assertEquals("MC-123460", verdict.error().mojiraId());
    }

    @Test
    void missingPackMetaFailsWithMojira123459() {
        ResourcePackResolver.LoadVerdict verdict = resolver.resolve(
                new ResourcePackSpec(tempDir.resolve("absent").toString(), null, "", true));

        assertFalse(verdict.loadable());
        assertEquals(PackMetaErrorKind.MISSING_META, verdict.error().kind());
        assertEquals("MC-123459", verdict.error().mojiraId());
    }

    @Test
    void sha1MismatchRejectsPack() throws IOException {
        Path zip = tempDir.resolve("rp-sha.zip");
        writeZip(zip, "pack.mcmeta", VALID_META);

        ResourcePackResolver.LoadVerdict verdict = resolver.resolve(
                new ResourcePackSpec(zip.toString(), null, "0000000000000000000000000000000000000000", true));

        assertFalse(verdict.loadable());
        assertFalse(verdict.metaParsed());
        assertEquals(40, verdict.sha1Actual().length());
        assertTrue(verdict.notes().stream().anyMatch(note -> note.contains("sha1 不一致")));
    }

    @Test
    void remoteUrlIsNotDownloadedHeadless() {
        ResourcePackResolver.LoadVerdict verdict = resolver.resolve(
                new ResourcePackSpec("https://example.invalid/rp.zip", null, "", true));

        assertFalse(verdict.loadable());
        assertFalse(verdict.metaParsed());
        assertTrue(verdict.notes().stream().anyMatch(note -> note.contains("未下载")));
        assertTrue(verdict.notes().stream().anyMatch(note -> note.contains("材质")));
    }

    @Test
    void suppliedMetaSkipsContentReading() {
        PackMeta meta = new PackMeta(46, "inline", null, null, null);

        ResourcePackResolver.LoadVerdict verdict = resolver.resolve(
                new ResourcePackSpec("https://example.invalid/rp.zip", meta, "", true));

        assertTrue(verdict.loadable());
        assertTrue(verdict.metaParsed());
        assertEquals(meta, verdict.meta());
    }

    private static void writeZip(Path target, String entryName, String content) throws IOException {
        try (OutputStream raw = Files.newOutputStream(target);
             ZipOutputStream zip = new ZipOutputStream(raw)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }
}
