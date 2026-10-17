package io.laplace.sprite.cheats;

import io.laplace.sprite.core.engine.SimulatedSession;
import io.laplace.sprite.core.model.TestSpec;
import io.laplace.sprite.core.model.SampleTier;
import io.laplace.sprite.core.model.SampleTierName;
import io.laplace.sprite.core.model.CheatType;
import io.laplace.sprite.core.model.ExpectedDetection;
import io.laplace.sprite.core.model.NetworkModel;
import io.laplace.sprite.core.model.PlayerType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 会话工厂：样本 × 档位组装与边界样本。 */
class CheatSessionFactoryTest {

    private static final String SCENARIO = "pvp";
    private static final long SEED = 7L;

    private final CheatCatalog catalog = new CheatCatalog();
    private final CheatSessionFactory factory = new CheatSessionFactory();

    @Test
    void buildsOneSessionPerSpecAndTier() {
        List<TestSpec> specs = catalog.loadDefaults();
        for (SampleTierName tier : SampleTierName.values()) {
            List<SimulatedSession> sessions = factory.buildSessions(specs, tier, SCENARIO, TestStreams.PLATFORM,
                    NetworkModel.defaults(), SEED, TestStreams.walk());
            long declared = specs.stream().filter(spec -> spec.tier(tier).isPresent()).count();
            assertEquals(declared, sessions.size(), tier.name());
            for (SimulatedSession session : sessions) {
                assertEquals(tier, session.tier());
                assertEquals(PlayerType.CHEAT, session.playerType());
                assertTrue(session.isCheat());
                assertEquals(SCENARIO, session.scenarioId());
                assertEquals(session.cheatSpec().tier(tier).orElseThrow().expected(), session.expected());
                assertFalse(session.stream().isEmpty(), session.cheatSpec().id());
            }
        }
    }

    @Test
    void allSpecsDeclareEveryTier() {
        List<TestSpec> specs = catalog.loadDefaults();
        for (SampleTierName tier : SampleTierName.values()) {
            List<SimulatedSession> sessions = factory.buildSessions(specs, tier, SCENARIO, TestStreams.PLATFORM,
                    NetworkModel.defaults(), SEED, TestStreams.walk());
            assertEquals(specs.size(), sessions.size(), tier.name());
        }
        assertEquals(new AnomalyMatrix().totalConfigurations(),
                specs.size() * SampleTierName.values().length);
    }

    @Test
    void boundarySessionsUseBoundaryExpectation() {
        List<TestSpec> specs = catalog.loadDefaults();
        List<SimulatedSession> sessions = factory.buildBoundarySessions(specs, SCENARIO, TestStreams.PLATFORM,
                NetworkModel.defaults(), SEED, TestStreams.walk());
        assertEquals(specs.size(), sessions.size());
        assertTrue(sessions.stream().allMatch(SimulatedSession::isBoundary));
        assertTrue(sessions.stream().allMatch(session -> session.expected().boundary()));
    }

    @Test
    void nullBaselineFallsBackToSyntheticStream() {
        List<TestSpec> specs = catalog.loadDefaults();
        List<SimulatedSession> sessions = factory.buildSessions(specs, SampleTierName.DEFAULT, SCENARIO,
                TestStreams.PLATFORM, NetworkModel.defaults(), SEED, null);
        assertEquals(specs.size(), sessions.size());
        assertTrue(sessions.stream().allMatch(session -> !session.stream().isEmpty()));
    }

    @Test
    void boundaryIsSynthesisedWhenSpecHasNoBoundaryTier() {
        TestSpec spec = new TestSpec("synthetic.no-boundary", "无边界样本", CheatType.SPEED,
                "detection-engine",
                List.of(new SampleTier(SampleTierName.DEFAULT, Map.of("speed", 1.4),
                        ExpectedDetection.detected())),
                null, "speed", "Wurst", null);
        List<SimulatedSession> sessions = factory.buildBoundarySessions(List.of(spec), SCENARIO,
                TestStreams.PLATFORM, NetworkModel.defaults(), SEED, TestStreams.walk());
        assertEquals(1, sessions.size());
        assertTrue(sessions.get(0).isBoundary());
        assertEquals(SampleTierName.DEFAULT, sessions.get(0).tier());
        assertFalse(sessions.get(0).stream().isEmpty());
    }
}
