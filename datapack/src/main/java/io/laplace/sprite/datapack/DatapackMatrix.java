package io.laplace.sprite.datapack;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Function;

/**
 * 数据包版本矩阵（15.8）：同一份数据包在多个服务端版本镜像上各起一列。
 *
 * <p>pack_format 与目标版本不兼容时不启动容器，直接记为该列在加载阶段失败——原版
 * {@code /datapack} 加载阶段就会报错，提前判失败省掉一次容器启动。</p>
 */
public final class DatapackMatrix {

    /** 失败发生的阶段，按执行先后排列。 */
    public enum FailureStage {
        /** pack_format 不兼容，数据包在加载阶段就被拒。 */
        LOAD,
        /** 该版本没有可用控制台。 */
        CONSOLE,
        ENABLE,
        RELOAD,
        GAMETEST,
        FUNCTION
    }

    /**
     * 矩阵的一列。
     *
     * @param mcVersion           服务端版本
     * @param packFormatCompatible pack_format 是否与目标版本兼容
     * @param passed              整列是否通过
     * @param failedStage         首个失败阶段；通过时为 null
     * @param result              首个失败用例的结果；通过时为 null
     * @param note                说明
     */
    public record MatrixCell(String mcVersion, boolean packFormatCompatible, boolean passed,
                             FailureStage failedStage, DatapackRunResult result, String note) {

        public String id() {
            return mcVersion;
        }
    }

    private final PackMeta meta;
    private final PackFormatTable formats;
    private final Function<String, DatapackConsole> consoles;
    private final List<MatrixCell> cells = new ArrayList<>();

    /**
     * @param meta     被测数据包的元数据
     * @param formats  版本对照表
     * @param consoles 版本 → 控制台；返回 null 表示该版本没有可用环境
     */
    public DatapackMatrix(PackMeta meta, PackFormatTable formats,
                          Function<String, DatapackConsole> consoles) {
        this.meta = meta;
        this.formats = formats;
        this.consoles = consoles;
    }

    /** 逐版本跑套件，返回本次产生的列。 */
    public List<MatrixCell> run(DatapackSuite suite, List<String> versions) {
        List<MatrixCell> produced = new ArrayList<>(versions.size());
        for (String version : versions) {
            produced.add(runColumn(suite, version));
        }
        cells.addAll(produced);
        return List.copyOf(produced);
    }

    private MatrixCell runColumn(DatapackSuite suite, String version) {
        if (!formats.compatible(version, meta)) {
            OptionalInt expected = formats.formatFor(version);
            String note = "pack_format %d 与 %s 期望的 %s 不兼容，加载阶段失败".formatted(meta.packFormat(),
                    version, expected.isPresent() ? expected.getAsInt() : "未知格式");
            return new MatrixCell(version, false, false, FailureStage.LOAD, null, note);
        }
        DatapackConsole console = consoles.apply(version);
        if (console == null) {
            return new MatrixCell(version, true, false, FailureStage.CONSOLE, null,
                    "版本 %s 没有可用控制台，本列未执行".formatted(version));
        }
        DatapackSmokeRunner runner = new DatapackSmokeRunner(console, version);
        DatapackRunResult firstFailure = null;
        List<String> notes = new ArrayList<>();
        for (DatapackCase testCase : suite.cases()) {
            DatapackRunResult result = runner.run(testCase);
            if (!result.passed() && firstFailure == null) {
                firstFailure = result;
            }
            if (result.notes() != null && !result.notes().isBlank()) {
                notes.add(testCase.id() + "：" + result.notes());
            }
        }
        boolean passed = firstFailure == null;
        FailureStage stage = passed ? null : stageOf(firstFailure.failedStep());
        return new MatrixCell(version, true, passed, stage, firstFailure,
                notes.isEmpty() ? null : String.join("；", notes));
    }

    private static FailureStage stageOf(String failedStep) {
        if (failedStep == null) {
            return null;
        }
        if (failedStep.startsWith("/datapack")) {
            return FailureStage.ENABLE;
        }
        if (failedStep.startsWith("/reload")) {
            return FailureStage.RELOAD;
        }
        if (failedStep.startsWith("/test")) {
            return FailureStage.GAMETEST;
        }
        if (failedStep.startsWith("/function")) {
            return FailureStage.FUNCTION;
        }
        return null;
    }

    public List<MatrixCell> cells() {
        return List.copyOf(cells);
    }

    public List<MatrixCell> failedCells() {
        return cells.stream().filter(cell -> !cell.passed()).toList();
    }

    /** 失败列与失败阶段的一行人话摘要。 */
    public List<String> failureSummary() {
        return cells.stream()
                .filter(cell -> !cell.passed())
                .map(cell -> "%s 失败于 %s 阶段：%s".formatted(cell.id(), cell.failedStage(), cell.note()))
                .toList();
    }

    public void clear() {
        cells.clear();
    }
}
