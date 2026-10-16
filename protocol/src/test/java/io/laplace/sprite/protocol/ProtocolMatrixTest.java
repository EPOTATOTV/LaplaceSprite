package io.laplace.sprite.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 协议矩阵（15.6）：相邻版本挑选、失败格与失败阶段标记。 */
class ProtocolMatrixTest {

    private static final List<String> VERSIONS = List.of("1.16.5", "1.17.1", "1.18.2", "1.19.4", "1.20.1",
            "1.20.4", "1.21.1");

    @Test
    void picksFiveAdjacentVersionsCentredOnServer() {
        assertEquals(List.of("1.18.2", "1.19.4", "1.20.1", "1.20.4", "1.21.1"),
                ProtocolMatrix.adjacent("1.20.1", VERSIONS));
    }

    @Test
    void clampsWindowAtBothEnds() {
        assertEquals(List.of("1.16.5", "1.17.1", "1.18.2", "1.19.4", "1.20.1"),
                ProtocolMatrix.adjacent("1.16.5", VERSIONS));
        assertEquals(List.of("1.18.2", "1.19.4", "1.20.1", "1.20.4", "1.21.1"),
                ProtocolMatrix.adjacent("1.21.1", VERSIONS));
    }

    @Test
    void keepsAllCandidatesWhenFewerThanMinimum() {
        assertEquals(List.of("1.20.1", "1.20.4"),
                ProtocolMatrix.adjacent("1.20.1", List.of("1.20.4", "1.20.1")));
    }

    @Test
    void unknownServerVersionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ProtocolMatrix.adjacent("1.9.9", VERSIONS));
    }

    @Test
    void viaProxyMatrixPassesAllAdjacentCells() {
        ProtocolMatrix matrix = new ProtocolMatrix(new SimulatedProtocolProbe());
        List<ProtocolMatrixCell> cells = matrix.run("1.20.1", VERSIONS);

        assertEquals(5, cells.size());
        assertTrue(cells.stream().allMatch(ProtocolMatrixCell::ok), "相邻 5 个版本跨度都小于 3，应全部通过");
        assertTrue(matrix.failedCells().isEmpty());
        assertTrue(matrix.failureSummary().isEmpty());

        ProtocolMatrixCell sameVersion = cellOf(cells, "1.20.1");
        assertEquals(ProtocolStage.ordered(), sameVersion.passedStages());
        assertTrue(sameVersion.viaProxy());
        assertFalse(sameVersion.bedrock());
    }

    @Test
    void withoutViaProxyDifferentVersionsFailAtLogin() {
        ProtocolMatrix matrix = new ProtocolMatrix(new SimulatedProtocolProbe()).withViaProxy(false);
        List<ProtocolMatrixCell> cells = matrix.run("1.20.1", VERSIONS);

        ProtocolMatrixCell older = cellOf(cells, "1.18.2");
        assertFalse(older.ok());
        assertEquals(ProtocolStage.LOGIN, older.failedStage());
        assertTrue(older.passedStages().isEmpty(), "登录就没过，后面的阶段不该算通过");

        ProtocolMatrixCell sameVersion = cellOf(cells, "1.20.1");
        assertTrue(sameVersion.ok(), "同版本不需要 Via");

        assertEquals(4, matrix.failedCells().size());
        assertTrue(matrix.failureSummary().get(0).contains("失败于 LOGIN 阶段"));
    }

    @Test
    void largeVersionGapFailsAtPlaceBlockEvenWithVia() {
        ProtocolMatrixCell seed = ProtocolMatrixCell.seed("1.21.1", "1.16.5", true, false);
        ProtocolMatrixCell cell = ProtocolMatrixCell.of(seed, new SimulatedProtocolProbe().run(seed));

        assertFalse(cell.ok());
        assertEquals(ProtocolStage.PLACE_BLOCK, cell.failedStage());
        assertEquals(List.of(ProtocolStage.LOGIN, ProtocolStage.JOIN_WORLD), cell.passedStages());
    }

    @Test
    void missingStageResultCountsAsFailure() {
        ProtocolMatrixCell seed = ProtocolMatrixCell.seed("1.20.1", "1.20.1", true, false);
        ProtocolMatrixCell cell = ProtocolMatrixCell.of(seed,
                List.of(ProtocolStageResult.pass(ProtocolStage.LOGIN, "ok")));

        assertFalse(cell.ok());
        assertEquals(ProtocolStage.JOIN_WORLD, cell.failedStage());
        assertEquals(List.of(ProtocolStage.LOGIN), cell.passedStages());
    }

    @Test
    void bedrockCellGoesThroughGeyserBridge() {
        ProtocolMatrix matrix = new ProtocolMatrix(new SimulatedProtocolProbe());
        ProtocolMatrixCell cell = matrix.runBedrock("1.21.1");

        assertTrue(cell.bedrock());
        assertTrue(cell.ok());
        assertEquals("bedrock", cell.clientVersion());
        assertEquals("1.21.1<-bedrock(bedrock)", cell.id());
        assertEquals(1, matrix.cells().size());
    }

    private static ProtocolMatrixCell cellOf(List<ProtocolMatrixCell> cells, String clientVersion) {
        return cells.stream()
                .filter(cell -> cell.clientVersion().equals(clientVersion))
                .findFirst()
                .orElseThrow(() -> new AssertionError("矩阵里没有客户端版本 " + clientVersion));
    }
}
