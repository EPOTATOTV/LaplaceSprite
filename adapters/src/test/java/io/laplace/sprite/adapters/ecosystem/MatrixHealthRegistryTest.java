package io.laplace.sprite.adapters.ecosystem;

import io.laplace.sprite.adapters.ecosystem.MatrixHealthRegistry.Mode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 矩阵健康度：降级 / 升级边界、持久化与升级触发的单格冒烟（14.9）。 */
class MatrixHealthRegistryTest {

    private static final String CELL = "fabric/1.21.11/0.19.5";

    @Test
    void downgradeHappensAtExactlyThreeConsecutivePasses(@TempDir Path tempDir) {
        MatrixHealthRegistry registry = new MatrixHealthRegistry(tempDir.resolve("health.json"));

        registry.record(CELL, true);
        registry.record(CELL, true);
        assertEquals(Mode.EVERY_RUN, registry.mode(CELL), "两次通过还不降级");

        registry.record(CELL, true);
        assertEquals(Mode.SAMPLE_ONE_THIRD, registry.mode(CELL), "第三次通过达到阈值");

        assertFalse(registry.shouldRun(CELL, 1));
        assertFalse(registry.shouldRun(CELL, 2));
        assertTrue(registry.shouldRun(CELL, 3), "每 3 轮跑第 1 轮");
    }

    @Test
    void promotionHappensAtExactlyTwoConsecutiveFailures(@TempDir Path tempDir) {
        MatrixHealthRegistry registry = new MatrixHealthRegistry(tempDir.resolve("health.json"));
        for (int i = 0; i < MatrixHealthRegistry.DOWNGRADE_AFTER_PASSES; i++) {
            registry.record(CELL, true);
        }
        assertEquals(Mode.SAMPLE_ONE_THIRD, registry.mode(CELL));

        registry.record(CELL, false);
        assertEquals(Mode.SAMPLE_ONE_THIRD, registry.mode(CELL), "抽样中失败一次还不升回必跑");

        registry.record(CELL, false);
        assertEquals(Mode.EVERY_RUN, registry.mode(CELL), "连续第二次失败达到阈值");
        assertTrue(registry.shouldRun(CELL, 1));
    }

    @Test
    void aPassResetsTheFailureStreak(@TempDir Path tempDir) {
        MatrixHealthRegistry registry = new MatrixHealthRegistry(tempDir.resolve("health.json"));

        registry.record(CELL, false);
        registry.record(CELL, false);
        registry.record(CELL, true);

        assertEquals(1, registry.health().get(0).consecutivePasses());
        assertEquals(0, registry.health().get(0).consecutiveFailures(), "通过要清零失败计数");

        registry.record(CELL, false);
        assertEquals(0, registry.health().get(0).consecutivePasses());
        assertEquals(1, registry.health().get(0).consecutiveFailures());
    }

    @Test
    void stateSurvivesRestart(@TempDir Path tempDir) {
        Path file = tempDir.resolve("nested").resolve("health.json");
        MatrixHealthRegistry first = new MatrixHealthRegistry(file);
        first.record(CELL, true);
        first.record(CELL, true);
        first.record(CELL, true);

        MatrixHealthRegistry reopened = new MatrixHealthRegistry(file);
        assertEquals(Mode.SAMPLE_ONE_THIRD, reopened.mode(CELL));
        assertEquals(3, reopened.health().get(0).consecutivePasses());
    }

    @Test
    void upgradeTriggersOnlyTheAffectedCell(@TempDir Path tempDir) {
        MatrixHealthRegistry registry = new MatrixHealthRegistry(tempDir.resolve("health.json"));
        registry.registerCell("fabric/1.21.11/0.19.5", List.of("modrinth:sodium"));
        registry.registerCell("fabric/1.21.1/0.16.14", List.of("modrinth:create-fabric"));

        assertEquals(List.of("fabric/1.21.11/0.19.5"),
                registry.cellsAffectedByUpgrade("modrinth:sodium"));
        assertEquals(List.of("fabric/1.21.1/0.16.14"),
                registry.cellsAffectedByUpgrade("modrinth:create-fabric"));
        assertTrue(registry.cellsAffectedByUpgrade("modrinth:viaversion").isEmpty());
    }

    @Test
    void recordingWithCoordinatesRegistersTheCell(@TempDir Path tempDir) {
        MatrixHealthRegistry registry = new MatrixHealthRegistry(tempDir.resolve("health.json"));
        registry.record(CELL, true, List.of("modrinth:lithium"));

        assertEquals(List.of(CELL), registry.cellsAffectedByUpgrade("modrinth:lithium"));
    }
}
