package io.laplace.sprite.report;

import io.laplace.sprite.core.model.FlagEvent;
import io.laplace.sprite.core.model.RegressionCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegressionLibraryTest {

    @TempDir
    Path dir;

    @Test
    void deduplicatesTrajectoryAndSurvivesRestart() {
        Path file = dir.resolve("regression.json");
        RegressionLibrary library = new RegressionLibrary(file);
        FlagEvent flag = FlagEvent.falsePositive("run-1", "P-1", "speed", 42L, "traj-1", "ev-1");

        RegressionCase first = library.add(flag, "traj-1", "commit-abc", "修复误报");
        RegressionCase duplicate = library.add(flag, "traj-1", "commit-xyz", "重复提交");

        assertEquals(first.id(), duplicate.id(), "同一轨迹应幂等返回既有用例");
        assertEquals(1, library.size());
        assertTrue(library.hasTrajectory("traj-1"));

        RegressionLibrary reopened = new RegressionLibrary(file);
        assertEquals(1, reopened.size());
        assertTrue(reopened.hasTrajectory("traj-1"), "回归库未在重启后恢复");
    }
}
