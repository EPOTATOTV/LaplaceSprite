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

/** PackTest 约定：data/&lt;ns&gt;/test/*.mcfunction 自动成为数据包冒烟用例。 */
class PackTestConventionTest {

    @TempDir
    Path tempDir;

    @Test
    void scansTestFunctionsByNamespaceAndName() throws IOException {
        Path pack = Files.createDirectories(tempDir.resolve("laplace-smoke"));
        Files.createDirectories(pack.resolve("data/zeta/test"));
        Files.createDirectories(pack.resolve("data/alpha/test"));
        Files.createDirectories(pack.resolve("data/alpha/function"));
        Files.writeString(pack.resolve("data/zeta/test/tick.mcfunction"), "say z");
        Files.writeString(pack.resolve("data/alpha/test/smoke.mcfunction"), "say a");
        Files.writeString(pack.resolve("data/alpha/function/not-a-test.mcfunction"), "say n");

        DatapackSuite suite = new PackTestConvention(pack).scan();

        assertEquals("laplace-smoke", suite.id());
        assertEquals("PackTest:laplace-smoke", suite.name());
        assertEquals(2, suite.cases().size());
        assertEquals(List.of("alpha.smoke", "zeta.tick"),
                suite.cases().stream().map(DatapackCase::id).toList());
        DatapackCase smoke = suite.cases().get(0);
        assertEquals("alpha:smoke", smoke.functionReference());
        assertEquals(AssertBackend.ASSERT_GAMETEST, smoke.assertBackend());
        assertEquals("laplace-smoke", smoke.packName());
    }

    @Test
    void validationAcceptsAWellFormedPack() throws IOException {
        Path pack = Files.createDirectories(tempDir.resolve("valid-pack"));
        Files.writeString(pack.resolve("pack.mcmeta"),
                "{\"pack\":{\"pack_format\":86,\"description\":\"26.2\"}}");
        Files.createDirectories(pack.resolve("data/alpha/test"));
        Files.writeString(pack.resolve("data/alpha/test/smoke.mcfunction"), "say ok");

        PackTestConvention.Validation validation = new PackTestConvention(pack).validate();

        assertTrue(validation.valid(), validation.issues().toString());
        assertEquals(1, validation.testFunctions());
        assertEquals("alpha:smoke", validation.cases().get(0).functionReference());
    }

    @Test
    void validationReportsMissingMetaEmptyFunctionsAndNoTests() throws IOException {
        Path pack = Files.createDirectories(tempDir.resolve("broken-pack"));
        Files.createDirectories(pack.resolve("data/alpha/test"));
        Files.writeString(pack.resolve("data/alpha/test/empty.mcfunction"), "  \n");

        PackTestConvention.Validation validation = new PackTestConvention(pack).validate();

        assertFalse(validation.valid());
        assertTrue(validation.issues().stream().anyMatch(issue -> issue.contains("pack.mcmeta")));
        assertTrue(validation.issues().stream().anyMatch(issue -> issue.contains("测试函数为空")));
    }

    @Test
    void packWithoutTestFunctionsYieldsEmptySuite() throws IOException {
        Path pack = Files.createDirectories(tempDir.resolve("empty-pack"));
        Files.createDirectories(pack.resolve("data/alpha/function"));

        DatapackSuite suite = new PackTestConvention(pack).scan();

        assertTrue(suite.cases().isEmpty());
        assertEquals("empty-pack", suite.id());
    }

    @Test
    void onlyFunctionsDirectlyUnderTestDirectoryCount() {
        assertTrue(PackTestConvention.isTestFunction(Path.of("data/alpha/test/smoke.mcfunction")));
        assertFalse(PackTestConvention.isTestFunction(Path.of("data/alpha/function/smoke.mcfunction")));
        assertFalse(PackTestConvention.isTestFunction(Path.of("data/alpha/test/smoke.json")));
    }
}
