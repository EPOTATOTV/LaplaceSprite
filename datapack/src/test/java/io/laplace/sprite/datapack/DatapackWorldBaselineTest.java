package io.laplace.sprite.datapack;

import io.laplace.sprite.sandbox.SandboxSpec;
import io.laplace.sprite.sandbox.SandboxTier;
import io.laplace.sprite.sandbox.WorldSnapshotStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 世界基线复用与 --level 接线（15.8）。 */
class DatapackWorldBaselineTest {

    private static final String VALID_META =
            "{\"pack\":{\"pack_format\":61,\"description\":\"smoke\"}}";

    @TempDir
    Path tempDir;

    @Test
    void restoresBaselineAndInstallsPackDirectory() throws IOException {
        Path data = tempDir.resolve("data");
        Files.createDirectories(data.resolve("world"));
        Files.writeString(data.resolve("world").resolve("level.dat"), "baseline");
        WorldSnapshotStore snapshots = new WorldSnapshotStore(tempDir.resolve("snapshots"));
        snapshots.snapshotBaseline("base", data);

        Path pack = Files.createDirectories(tempDir.resolve("smoke-pack"));
        Files.writeString(pack.resolve("pack.mcmeta"), VALID_META);
        Path function = Files.createDirectories(pack.resolve("data").resolve("smoke").resolve("function"));
        Files.writeString(function.resolve("check.mcfunction"), "say ok");

        DatapackWorldBaseline.Prepared prepared = new DatapackWorldBaseline(snapshots)
                .prepare(spec(data), "base", pack);

        assertTrue(Files.isRegularFile(data.resolve("world").resolve("level.dat")),
                "回灌后基线世界应回到 /data");
        assertTrue(Files.isRegularFile(prepared.datapacksDir().resolve("smoke-pack").resolve("pack.mcmeta")));
        assertTrue(Files.isRegularFile(prepared.datapacksDir()
                .resolve("smoke-pack").resolve("data").resolve("smoke").resolve("function")
                .resolve("check.mcfunction")));
        assertEquals(data.resolve("world"), prepared.worldDir());
        assertEquals(List.of("--level", "world"), prepared.levelArguments());
        assertEquals("world", prepared.spec().extraEnv().get("LEVEL"));
        assertTrue(prepared.notes().stream().anyMatch(note -> note.contains("基线快照")));
    }

    @Test
    void missingBaselineIsReportedWithoutFailing() {
        Path data = tempDir.resolve("data");
        WorldSnapshotStore snapshots = new WorldSnapshotStore(tempDir.resolve("snapshots"));
        Path pack = createPack(tempDir.resolve("smoke-pack"));

        DatapackWorldBaseline.Prepared prepared = new DatapackWorldBaseline(snapshots)
                .prepare(spec(data), "base", pack);

        assertTrue(Files.isDirectory(prepared.datapacksDir()));
        assertTrue(prepared.notes().stream().anyMatch(note -> note.contains("未找到基线快照")));
    }

    @Test
    void extractsZipPackIntoDatapacks() throws IOException {
        Path data = tempDir.resolve("data");
        Files.createDirectories(data.resolve("world"));
        WorldSnapshotStore snapshots = new WorldSnapshotStore(tempDir.resolve("snapshots"));
        snapshots.snapshotBaseline("base", data);
        Path zip = tempDir.resolve("zipped.zip");
        writeZip(zip, "pack.mcmeta", VALID_META);

        DatapackWorldBaseline.Prepared prepared = new DatapackWorldBaseline(snapshots)
                .prepare(spec(data), "base", zip);

        assertTrue(Files.isRegularFile(prepared.datapacksDir().resolve("zipped").resolve("pack.mcmeta")));
    }

    private static SandboxSpec spec(Path dataDir) {
        return SandboxSpec.forTier("dp-smoke", SandboxTier.SMOKE, "itzg/minecraft-server:java21",
                "PAPER", "1.21.4", List.of(), dataDir, 25601);
    }

    private static Path createPack(Path pack) {
        try {
            Files.createDirectories(pack);
            Files.writeString(pack.resolve("pack.mcmeta"), VALID_META);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        return pack;
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
