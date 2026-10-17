package io.laplace.sprite.mixin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 版本分层兼容检查（15.11 P2）：Loader 声明与 MIXIN 子系统版本对照。 */
class MixinLayerCheckerTest {

    private static final String MODERN_MOD_JSON = """
            {
              "schemaVersion": 1,
              "id": "questmod",
              "version": "1.0.0",
              "depends": {
                "fabricloader": ">=0.15.0",
                "minecraft": "1.21.1"
              }
            }
            """;

    private static final String OLD_MOD_JSON = """
            {
              "schemaVersion": 1,
              "id": "legacymod",
              "version": "1.0.0",
              "depends": {
                "fabricloader": ">=0.11.0"
              }
            }
            """;

    @Test
    void declaredLoaderAndObservedMixinAreConsistent() {
        MixinLayerBaseline baseline = MixinLayerChecker.check(MODERN_MOD_JSON,
                "[main/INFO] [mixin/]: SpongePowered MIXIN Subsystem Version=0.8.5 Source=launch\n");

        assertEquals("0.15.0", baseline.loaderVersionDeclared());
        assertEquals("0.8.5", baseline.subsystemVersionObserved());
        assertFalse(baseline.simulatedOldBehaviour());
        assertTrue(baseline.consistent(), String.join("；", baseline.notes()));
    }

    @Test
    void loaderTwelvePlusWithOldMixinSubsystemIsInconsistent() {
        MixinLayerBaseline baseline = MixinLayerChecker.check(MODERN_MOD_JSON,
                "[main/INFO] [mixin/]: SpongePowered MIXIN Subsystem Version=0.7.4 Source=launch\n");

        assertFalse(baseline.consistent());
        assertTrue(baseline.notes().stream().anyMatch(note -> note.contains("0.8")),
                String.join("；", baseline.notes()));
    }

    @Test
    void oldLoaderDeclarationEnablesSimulatedOldBehaviour() {
        MixinLayerBaseline baseline = MixinLayerChecker.check(OLD_MOD_JSON,
                "[main/INFO] [mixin/]: SpongePowered MIXIN Subsystem Version=0.8.5 Source=launch\n");

        assertEquals("0.11.0", baseline.loaderVersionDeclared());
        assertTrue(baseline.simulatedOldBehaviour());
        assertTrue(baseline.consistent(), String.join("；", baseline.notes()));
        assertTrue(baseline.notes().stream().anyMatch(note -> note.contains("模拟")),
                String.join("；", baseline.notes()));
    }

    @Test
    void twoSubsystemVersionLinesFailUniqueness() {
        MixinLayerBaseline baseline = MixinLayerChecker.check(MODERN_MOD_JSON,
                "[main/INFO] [mixin/]: SpongePowered MIXIN Subsystem Version=0.8.5 Source=launch\n"
                        + "[main/INFO] [mixin/]: SpongePowered MIXIN Subsystem Version=0.8.5 Source=duplicate\n");

        assertFalse(baseline.consistent());
        assertTrue(baseline.notes().stream().anyMatch(note -> note.contains("要求仅一条唯一版本号")),
                String.join("；", baseline.notes()));
    }

    @Test
    void missingLoaderDeclarationCannotBeVerified() {
        String json = "{\"schemaVersion\":1,\"id\":\"noloader\",\"version\":\"1.0.0\"}";

        MixinLayerBaseline baseline = MixinLayerChecker.check(json,
                "[main/INFO] [mixin/]: SpongePowered MIXIN Subsystem Version=0.8.5 Source=launch\n");

        assertFalse(baseline.consistent());
        assertTrue(baseline.notes().stream().anyMatch(note -> note.contains("未声明")),
                String.join("；", baseline.notes()));
    }
}
