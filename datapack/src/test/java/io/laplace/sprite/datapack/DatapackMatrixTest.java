package io.laplace.sprite.datapack;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 数据包版本矩阵（15.8）：不兼容列在加载阶段失败，兼容列逐版本执行。 */
class DatapackMatrixTest {

    private static final String LIST_REPLY =
            "There are 2 data packs enabled: [vanilla (built-in)], [file/smoke (world)]";
    private static final DatapackSuite SUITE = new DatapackSuite("smoke", "冒烟", List.of(
            new DatapackCase("smoke", "smoke", "check", "smoke",
                    "Steve has 7 [laplace_smoke]", AssertBackend.ENDS_WITH)));

    @Test
    void marksIncompatibleColumnFailedAtLoadStage() {
        PackMeta meta = new PackMeta(61, "1.21.4 pack", List.of(), List.of(), Map.of());
        DatapackMatrix matrix = new DatapackMatrix(meta, new PackFormatTable(), version -> {
            if ("1.21.5".equals(version)) {
                throw new IllegalStateException("不兼容列不应启动容器");
            }
            return okConsole();
        });

        List<DatapackMatrix.MatrixCell> cells = matrix.run(SUITE, List.of("1.21.4", "1.21.5"));

        assertEquals(2, cells.size());
        assertTrue(cells.get(0).passed());
        assertTrue(cells.get(0).packFormatCompatible());
        assertNull(cells.get(0).failedStage());

        DatapackMatrix.MatrixCell incompatible = cells.get(1);
        assertFalse(incompatible.passed());
        assertFalse(incompatible.packFormatCompatible());
        assertEquals(DatapackMatrix.FailureStage.LOAD, incompatible.failedStage(),
                "pack_format 不兼容必须记在加载阶段");
        assertNull(incompatible.result(), "没启动容器就没有运行结果");
        assertTrue(incompatible.note().contains("pack_format 61"));
        assertTrue(incompatible.note().contains("71"));
        assertEquals(1, matrix.failedCells().size());
        assertTrue(matrix.failureSummary().get(0).contains("LOAD"));
    }

    @Test
    void supportedFormatsRunsBothColumns() {
        PackMeta meta = new PackMeta(61, "61..71", List.of(new PackFormatRange(61, 71)),
                List.of(), Map.of());
        DatapackMatrix matrix = new DatapackMatrix(meta, new PackFormatTable(), version -> okConsole());

        List<DatapackMatrix.MatrixCell> cells = matrix.run(SUITE, List.of("1.21.4", "1.21.5"));

        assertTrue(cells.stream().allMatch(DatapackMatrix.MatrixCell::passed));
        assertEquals(2, matrix.cells().size());
    }

    @Test
    void functionFailureIsReportedWithStage() {
        PackMeta meta = new PackMeta(61, "1.21.4 pack", List.of(), List.of(), Map.of());
        DatapackMatrix matrix = new DatapackMatrix(meta, new PackFormatTable(),
                version -> command -> switch (command) {
                    case DatapackSmokeRunner.COMMAND_DATAPACK_LIST -> LIST_REPLY;
                    case DatapackSmokeRunner.COMMAND_RELOAD -> "Reloaded 2 data packs";
                    case "/function smoke:check" -> "smoke ok\nSteve has 1 [laplace_smoke]";
                    default -> "";
                });

        DatapackMatrix.MatrixCell cell = matrix.run(SUITE, List.of("1.21.4")).get(0);

        assertFalse(cell.passed());
        assertEquals(DatapackMatrix.FailureStage.FUNCTION, cell.failedStage());
        assertTrue(cell.note().contains("未以"));
    }

    @Test
    void missingConsoleMarksColumnWithoutRunning() {
        PackMeta meta = new PackMeta(71, "1.21.5 pack", List.of(), List.of(), Map.of());
        DatapackMatrix matrix = new DatapackMatrix(meta, new PackFormatTable(), version -> null);

        DatapackMatrix.MatrixCell cell = matrix.run(SUITE, List.of("1.21.5")).get(0);

        assertFalse(cell.passed());
        assertEquals(DatapackMatrix.FailureStage.CONSOLE, cell.failedStage());
        assertTrue(cell.note().contains("没有可用控制台"));
    }

    private static DatapackConsole okConsole() {
        return command -> switch (command) {
            case DatapackSmokeRunner.COMMAND_DATAPACK_LIST -> LIST_REPLY;
            case DatapackSmokeRunner.COMMAND_RELOAD -> "Reloaded 2 data packs";
            case "/function smoke:check" -> "smoke ok\nSteve has 7 [laplace_smoke]";
            default -> "";
        };
    }
}
