package io.laplace.sprite.adapters.ecosystem;

import io.laplace.sprite.core.model.PlatformFamily;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** OptiFine 闭源探针（14.9）。 */
class ClosedSourceProbeTest {

    @Test
    void onlyForgeOneTwentyOneRunsTheProbe() {
        ClosedSourceProbe.ProbeDecision decision = ClosedSourceProbe.decide(PlatformFamily.FORGE, "1.20.1");

        assertTrue(decision.run());
        assertEquals("1.20.1", decision.spec().minecraftVersion());
        assertEquals("forge", decision.spec().loader());
    }

    @Test
    void oneTwentyOneAndLaterAreSkippedWithTheUnsupportedNote() {
        ClosedSourceProbe.ProbeDecision decision = ClosedSourceProbe.decide(PlatformFamily.FORGE, "1.21.11");

        assertFalse(decision.run());
        assertEquals(ClosedSourceProbe.UNSUPPORTED_NOTE, decision.reason());
        assertNull(decision.spec());
        assertFalse(ClosedSourceProbe.decide(PlatformFamily.FORGE, "26.1").run());
    }

    @Test
    void neoForgeIsAlwaysUnsupported() {
        ClosedSourceProbe.ProbeDecision decision = ClosedSourceProbe.decide(PlatformFamily.NEOFORGE, "1.20.1");

        assertFalse(decision.run());
        assertEquals(ClosedSourceProbe.UNSUPPORTED_NOTE, decision.reason());
    }

    @Test
    void otherLoadersAreOutOfScope() {
        assertFalse(ClosedSourceProbe.decide(PlatformFamily.FABRIC, "1.20.1").run());
        assertFalse(ClosedSourceProbe.decide(PlatformFamily.PAPER, "1.20.1").run());
    }

    @Test
    void probeIsPinnedAndNeverPartOfTheAutomatedGate() {
        ClosedSourceProbe.ProbeSpec spec = ClosedSourceProbe.pinned().orElseThrow();

        assertTrue(spec.downloadPage().startsWith("https://optifine.net/"), spec.downloadPage());
        assertTrue(spec.sha256().matches("[0-9a-f]{64}"), "SHA256 必须是 64 位小写十六进制");
        assertFalse(ClosedSourceProbe.includedInAutomatedGate());
        assertFalse(ClosedSourceProbe.versionApiBacked(), "闭源探针不走版本矩阵 API");
        assertEquals(3, ClosedSourceProbe.ASSERTIONS.size());
    }
}
