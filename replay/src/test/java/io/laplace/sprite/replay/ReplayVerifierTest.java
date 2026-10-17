package io.laplace.sprite.replay;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayVerifierTest {

    private final ReplayVerifier verifier = new ReplayVerifier();

    @Test
    void matchesOnCrashSignatureEvenWhenTickCountDiffers() {
        ReplayExpectation expected = new ReplayExpectation("out-of-memory", null, 1200);
        ReplayObservation observed = new ReplayObservation("out-of-memory", null, 1204);

        ReplayResult result = verifier.verify(expected, observed);

        assertTrue(result.matched());
        assertTrue(result.sameCrashSignature());
        assertFalse(result.sameLogFeature());
        assertTrue(result.notes().contains(ReplayVerifier.DETERMINISM_NOTE));
        assertTrue(result.notes().stream().anyMatch(note -> note.contains("tick")));
    }

    @Test
    void doesNotClaimMatchWhenOnlyTickCountIsEqual() {
        ReplayExpectation expected = new ReplayExpectation("out-of-memory", null, 1200);
        ReplayObservation observed = new ReplayObservation("mixin-apply-failed", null, 1200);

        ReplayResult result = verifier.verify(expected, observed);

        assertFalse(result.matched());
        assertFalse(result.sameCrashSignature());
        assertFalse(result.sameLogFeature());
    }

    @Test
    void matchesOnLogFeatureAlone() {
        ReplayExpectation expected = new ReplayExpectation(null, "Tick took 120ms", 600);
        ReplayObservation observed = new ReplayObservation("unmatched", "Tick took 120ms", 605);

        ReplayResult result = verifier.verify(expected, observed);

        assertTrue(result.matched());
        assertFalse(result.sameCrashSignature());
        assertTrue(result.sameLogFeature());
    }

    @Test
    void cleanRecordingOnlyMatchesCleanReplay() {
        ReplayExpectation clean = new ReplayExpectation(null, null, 100);

        assertTrue(verifier.verify(clean, new ReplayObservation(null, null, 100)).matched());
        assertFalse(verifier.verify(clean,
                new ReplayObservation("out-of-memory", null, 100)).matched());
    }
}
