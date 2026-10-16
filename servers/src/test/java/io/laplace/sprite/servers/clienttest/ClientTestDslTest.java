package io.laplace.sprite.servers.clienttest;

import io.laplace.sprite.core.io.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 声明式 DSL（15.7）：JSON 往返与打包示例套件。 */
class ClientTestDslTest {

    @Test
    void suiteRoundTripsThroughJson() {
        ClientTestSuite suite = new ClientTestSuite("s-1", "冒烟", List.of(
                new ClientTestCase("c-1", "开服冒烟", "1.21.1", "fabric", "21", "linux",
                        List.of(ClientDslStep.of(ClientDslOp.SEND, "minecraft:reload"),
                                new ClientDslStep(ClientDslOp.EXPECT_LOG_ENDS_WITH, "Reloaded 3 data packs",
                                        "断言日志尾部"),
                                ClientDslStep.of(ClientDslOp.SCREENSHOT, null)))));

        assertEquals(suite, Json.read(Json.write(suite), ClientTestSuite.class));
    }

    @Test
    void bundledSmokeSuiteCoversCommandAndLogAssertion() {
        ClientTestSuite suite = new ClientTestSuiteLoader().loadBundledSmokeSuite();

        assertEquals("smoke-pack", suite.id());
        assertEquals(2, suite.cases().size());

        ClientTestCase packReload = caseById(suite, "pack-reload");
        assertEquals(ClientDslOp.WAIT_TICKS, packReload.steps().get(0).op(), "非代码用户先等开服");
        assertEquals(ClientDslOp.SEND, packReload.steps().get(1).op());
        assertEquals("minecraft:reload", packReload.steps().get(1).value());
        assertEquals(ClientDslOp.EXPECT_LOG_ENDS_WITH, packReload.steps().get(2).op());
        assertEquals("Reloaded 3 data packs", packReload.steps().get(2).value());
        assertEquals(ClientDslOp.SCREENSHOT, packReload.steps().get(3).op());

        ClientTestCase joinWorld = caseById(suite, "join-world");
        assertEquals(ClientDslOp.WAIT_FOR_RENDER, joinWorld.steps().get(1).op());
        assertEquals(ClientDslOp.SCREENSHOT, joinWorld.steps().get(2).op());
    }

    @Test
    void suiteLoadsFromJsonFile(@TempDir Path dir) {
        ClientTestSuiteLoader loader = new ClientTestSuiteLoader();
        ClientTestSuite bundled = loader.loadBundledSmokeSuite();
        Path file = dir.resolve("suite.json");
        Json.writeFile(file, bundled);

        assertEquals(bundled, loader.load(file));
    }

    private static ClientTestCase caseById(ClientTestSuite suite, String id) {
        return suite.cases().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }
}
