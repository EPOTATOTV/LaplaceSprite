package io.laplace.sprite.adapters.ecosystem;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 性能模组矩阵与红格规则（14.9）。 */
class PerformanceMatrixTest {

    @Test
    void clientMatrixHasTheFiveDocumentedVariants() {
        List<RenderPipelineCell> cells = PerformanceMatrix.clientCells();

        assertEquals(List.of("+Lithium", "+Sodium", "+Sodium+Iris", "+OptiFine"),
                cells.subList(1, 5).stream().map(RenderPipelineCell::displayName).toList());
        assertEquals("裸原版", cells.get(0).displayName());
        assertEquals(List.of("sodium", "iris"), cells.get(3).mods());
        assertTrue(cells.stream().allMatch(cell -> cell.assertions().equals(RenderPipelineCell.CLIENT_ASSERTIONS)));
    }

    @Test
    void clientAssertionsFollowTheDocumentedSequence() {
        assertEquals(List.of("XVFB 下启动到标题界面",
                        "进入预设世界快照",
                        "等待区块渲染完成",
                        "截图作为证据",
                        "日志无 mixin / render error",
                        "FPS 基线不低于阈值"),
                RenderPipelineCell.CLIENT_ASSERTIONS);
    }

    @Test
    void sodiumAndOptiFineAreMutuallyExclusive() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new RenderPipelineCell(PerformanceVariant.SODIUM, List.of("sodium", "optifine")));
        assertTrue(ex.getMessage().contains("Sodium 与 OptiFine 互斥"), ex.getMessage());

        PerformanceMatrix.CellVerdict verdict = PerformanceMatrix.validate(List.of("sodium", "optifine"));
        assertFalse(verdict.valid());
        assertTrue(verdict.reason().contains("闭源"), verdict.reason());
    }

    @Test
    void irisWithoutSodiumIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new RenderPipelineCell(PerformanceVariant.SODIUM_IRIS, List.of("iris")));
        assertTrue(ex.getMessage().contains("Iris 强依赖 Sodium"), ex.getMessage());

        assertFalse(PerformanceMatrix.validate(List.of("iris")).valid());
        assertTrue(PerformanceMatrix.validate(List.of("sodium", "iris")).valid());
    }

    @Test
    void serverLineOnlyValidatesLithiumTickBehaviour() {
        List<RenderPipelineCell> cells = PerformanceMatrix.serverCells();

        assertEquals(2, cells.size());
        assertEquals(List.of("裸原版", "+Lithium"),
                cells.stream().map(RenderPipelineCell::displayName).toList());
        assertTrue(cells.stream().allMatch(cell -> cell.assertions().equals(RenderPipelineCell.SERVER_ASSERTIONS)));
        assertThrows(IllegalArgumentException.class,
                () -> RenderPipelineCell.server(PerformanceVariant.OPTIFINE));
    }
}
