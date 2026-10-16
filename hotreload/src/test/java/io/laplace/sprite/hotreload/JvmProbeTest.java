package io.laplace.sprite.hotreload;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** JVM 探测（15.2）：输入全由调用方给，探测离线可复现。 */
class JvmProbeTest {

    @Test
    void hotSpotIsDetectedFromVersionOutput() {
        JvmProbe probe = JvmProbe.of(Map.of("java.vm.name", "OpenJDK 64-Bit Server VM"),
                "openjdk version \"21.0.5\" 2024-10-15");

        assertEquals(JvmKind.HOTSPOT, probe.kind());
        assertFalse(probe.supportsEnhancedRedefinition());
        assertTrue(probe.description().contains("OpenJDK"));
    }

    @Test
    void jbrAndDcevmAreDetected() {
        assertEquals(JvmKind.JETBRAINS_RUNTIME, JvmProbe.of(
                Map.of("java.vendor", "JetBrains s.r.o.", "java.vm.name", "OpenJDK 64-Bit Server VM"), "").kind());
        assertEquals(JvmKind.JETBRAINS_RUNTIME,
                JvmProbe.of(Map.of(), "OpenJDK 64-Bit Server VM (JBR 21)").kind());
        assertEquals(JvmKind.DCEVM,
                JvmProbe.of(Map.of(), "Java HotSpot(TM) 64-Bit Server VM (DCEVM-21)").kind());
        assertTrue(JvmProbe.of(Map.of(JvmProbe.KIND_PROPERTY, "dcevm"), "").kind().supportsEnhancedRedefinition());
    }

    @Test
    void explicitOverrideWins() {
        JvmProbe probe = JvmProbe.of(Map.of(JvmProbe.KIND_PROPERTY, "jetbrains-runtime",
                "java.vm.name", "OpenJDK 64-Bit Server VM"), "");

        assertEquals(JvmKind.JETBRAINS_RUNTIME, probe.kind());
        assertThrows(IllegalArgumentException.class,
                () -> JvmProbe.of(Map.of(JvmProbe.KIND_PROPERTY, "j9"), "").kind());
    }

    @Test
    void enhancedRedefinitionFlagIsReadFromArgs() {
        JvmProbe probe = JvmProbe.of(Map.of(JvmProbe.ARGS_PROPERTY,
                "-Xmx2G " + JvmProbe.ENHANCED_REDEFINITION_FLAG), "");

        assertTrue(probe.enhancedRedefinitionRequested());
        assertTrue(probe.supportsEnhancedRedefinition());
        assertFalse(JvmProbe.of(Map.of(), "").enhancedRedefinitionRequested());
    }

    @Test
    void unknownWhenNoEvidenceOnHand() {
        assertEquals(JvmKind.UNKNOWN, JvmProbe.of(Map.of(), "").kind());
        JvmProbe system = JvmProbe.system();
        assertNotNull(system.kind());
        assertFalse(system.inputs().isEmpty(), "系统属性应被读入");
    }
}
