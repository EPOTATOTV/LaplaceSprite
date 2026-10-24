package io.laplace.sprite.report;

import io.laplace.sprite.core.model.FlagEvent;
import io.laplace.sprite.core.model.TestRun;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * PACC 误报到回归库的自动同步（升级实施文档 v2 L2-06）。
 *
 * <p>夜间基准跑完后，把 {@link TestRun#falsePositives()} 写进回归台账，并按轨迹去重生成
 * 回归用例。没有轨迹的误报只入台账、不造用例——没有回放入口的用例无法验证修复。</p>
 */
public final class PaccRegressionSync {

    /**
     * 同步结果。
     *
     * @param falsePositives     误报 flag 数
     * @param addedCases         新增回归用例数
     * @param skippedWithoutTrajectory 缺轨迹而未生成用例的误报数
     */
    public record Summary(int falsePositives, int addedCases, int skippedWithoutTrajectory) {
    }

    private final Path workspace;

    public PaccRegressionSync(Path workspace) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
    }

    public Summary sync(List<TestRun> runs) {
        List<TestRun> source = runs == null ? List.of() : List.copyOf(runs);
        RegressionLibrary library = new RegressionLibrary(workspace.resolve("regression.json"));
        RegressionLedger ledger = new RegressionLedger(workspace.resolve("regression"));

        int falsePositives = 0;
        int added = 0;
        int skipped = 0;
        Set<String> uniqueFlags = new HashSet<>();
        for (TestRun run : source) {
            for (FlagEvent flag : run.falsePositives()) {
                if (!uniqueFlags.add(flag.id())) {
                    continue;
                }
                falsePositives++;
                ledger.recordFlags(List.of(flag));
                String trajectory = flag.trajectoryRef();
                if (trajectory == null || trajectory.isBlank()) {
                    skipped++;
                    continue;
                }
                int before = library.size();
                library.add(flag, trajectory, "pacc-nightly",
                        "PACC 夜间基准误报自动入库（L2-06，run " + run.id() + "）");
                if (library.size() > before) {
                    added++;
                }
            }
        }
        ledger.recordCases(library.all());
        ledger.writeHealth();
        return new Summary(falsePositives, added, skipped);
    }
}
