package io.laplace.sprite.datapack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** pack.mcmeta 解析与三条 Mojira 特征（15.8）。 */
class PackMetaParserTest {

    @TempDir
    Path tempDir;

    private final PackMetaParser parser = new PackMetaParser();

    @Test
    void parsesValidMetaWithRangeAndFilter() {
        String content = """
                {
                  "pack": {
                    "pack_format": 61,
                    "description": "Smoke pack",
                    "supported_formats": [46, 71],
                    "filter": [{"namespace": "smoke", "path": "data"}]
                  }
                }
                """;

        PackMetaResult result = parser.parse("pack.mcmeta", content);

        assertTrue(result.ok(), () -> "有效 pack.mcmeta 应解析成功: " + result.error());
        PackMeta meta = result.meta();
        assertEquals(61, meta.packFormat());
        assertEquals("Smoke pack", meta.description());
        assertEquals(List.of(new PackFormatRange(46, 71)), meta.supportedFormats());
        assertEquals(List.of("smoke"), meta.namespaceHints());
        assertTrue(meta.supports(71));
        assertEquals(1, meta.rawFields().size(), "顶层只有 pack 一个字段");
    }

    @Test
    void flattensTextComponentDescription() {
        PackMetaResult result = parser.parse("pack.mcmeta",
                "{\"pack\":{\"pack_format\":15,\"description\":{\"text\":\"Smoke\"}}}");

        assertTrue(result.ok());
        assertEquals("Smoke", result.meta().description());
    }

    @Test
    void malformedJsonReportsJsonSyntaxWithTrackedPath() {
        String content = "{\"pack\": {\"pack_format\": 15, \"description\": \"hello}";

        PackMetaResult result = parser.parse("world/datapacks/broken/pack.mcmeta", content);

        assertFalse(result.ok());
        PackMetaError error = result.error();
        assertEquals(PackMetaErrorKind.JSON_SYNTAX, error.kind());
        assertEquals("MC-123460", error.mojiraId());
        assertEquals("$.pack.description", error.jsonPath());
        assertEquals("world/datapacks/broken/pack.mcmeta", error.file());
    }

    @Test
    void missingFileReportsMojira123459() {
        PackMetaResult result = parser.parseFile(tempDir.resolve("broken").resolve("pack.mcmeta"));

        assertFalse(result.ok());
        assertEquals(PackMetaErrorKind.MISSING_META, result.error().kind());
        assertEquals("MC-123459", result.error().mojiraId());
        assertEquals("$.pack", result.error().jsonPath());
    }

    @Test
    void missingPackFormatIsStructuredError() {
        PackMetaResult result = parser.parse("pack.mcmeta", "{\"pack\":{\"description\":\"no format\"}}");

        assertFalse(result.ok());
        assertEquals(PackMetaErrorKind.MISSING_PACK_FORMAT, result.error().kind());
        assertEquals("$.pack.pack_format", result.error().jsonPath());
    }

    @Test
    void parsePackResolvesPackMcmetaInDirectory() throws IOException {
        Path pack = Files.createDirectories(tempDir.resolve("pack"));
        Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":48,\"description\":\"x\"}}");

        PackMetaResult result = parser.parsePack(pack);

        assertTrue(result.ok());
        assertEquals(48, result.meta().packFormat());
    }

    @Test
    void logShape123460KeepsGsonPath() {
        String line = "com.google.gson.JsonSyntaxException: com.google.gson.stream.MalformedJsonException: "
                + "Unterminated object at line 1 column 45 path $.pack.description";

        PackMetaError error = PackMetaParser.fromLog(line).orElseThrow();

        assertEquals(PackMetaErrorKind.JSON_SYNTAX, error.kind());
        assertEquals("MC-123460", error.mojiraId());
        assertEquals("$.pack.description", error.jsonPath());
        assertEquals("pack.mcmeta", error.file());
    }

    @Test
    void logShape123459ExtractsPackDirectory() {
        String line = "java.lang.IllegalStateException: Couldn't find pack meta in world/datapacks/broken";

        PackMetaError error = PackMetaParser.fromLog(line).orElseThrow();

        assertEquals(PackMetaErrorKind.MISSING_META, error.kind());
        assertEquals("MC-123459", error.mojiraId());
        assertEquals("world/datapacks/broken", error.file());
        assertEquals("$.pack", error.jsonPath());
    }

    @Test
    void logShape310240RequiresWorldLoadWording() {
        String worldLoad = "Failed to load datapacks, can't proceed with server load: "
                + "com.google.gson.JsonSyntaxException: MalformedJsonException at pack.mcmeta";

        PackMetaError error = PackMetaParser.fromLog(worldLoad).orElseThrow();

        assertEquals(PackMetaErrorKind.WORLD_LOAD_FAILURE, error.kind());
        assertEquals("MC-310240", error.mojiraId());
        assertTrue(PackMetaParser.fromLog("MalformedJsonException in mod config").isEmpty(),
                "没有 pack 上下文的 JSON 异常不该命中");
    }
}
