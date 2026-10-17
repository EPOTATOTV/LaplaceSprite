package io.laplace.sprite.simulator;

import io.laplace.sprite.core.engine.SimulatedSession;
import io.laplace.sprite.core.model.BehaviorProfile;
import io.laplace.sprite.core.model.BehaviorTrajectory;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.model.PlayerType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BehaviorTrajectoryGeneratorTest {

    private static final PlatformKey PLATFORM = PlatformKey.of(PlatformFamily.PAPER, "1.21");

    private final BehaviorTrajectoryGenerator generator = new BehaviorTrajectoryGenerator();

    @Test
    void sameSeedProducesIdenticalEvents() {
        BehaviorTrajectory first = generator.generate("legit-a", BehaviorProfile.SPRINT, "pvp", 12345L, 120,
                PLATFORM);
        BehaviorTrajectory second = generator.generate("legit-a", BehaviorProfile.SPRINT, "pvp", 12345L, 120,
                PLATFORM);
        assertEquals(first.events(), second.events());
        assertEquals(first.seed(), second.seed());
        assertTrue(first.eventCount() > 0);
    }

    @Test
    void differentSeedsProduceDifferentEvents() {
        BehaviorTrajectory first = generator.generate("legit-a", BehaviorProfile.SPRINT, "pvp", 12345L, 120,
                PLATFORM);
        BehaviorTrajectory other = generator.generate("legit-a", BehaviorProfile.SPRINT, "pvp", 12346L, 120,
                PLATFORM);
        assertNotEquals(first.events(), other.events());
    }

    @Test
    void cohortUsesDistinctPlayersAndSeeds() {
        List<BehaviorTrajectory> cohort = generator.cohort(4, BehaviorProfile.WALK, "mining", 500L, PLATFORM,
                60);
        assertEquals(4, cohort.size());
        assertEquals(List.of("legit-0", "legit-1", "legit-2", "legit-3"),
                cohort.stream().map(BehaviorTrajectory::id).toList());
        assertEquals(List.of(500L, 501L, 502L, 503L),
                cohort.stream().map(BehaviorTrajectory::seed).toList());
        assertTrue(cohort.get(0).events().stream().allMatch(event -> "legit-0".equals(event.playerId())));
    }

    @Test
    void networkRefFollowsScenario() {
        assertEquals("lte", generator.generate("legit-a", BehaviorProfile.WALK, "reconnect", 1L, 10,
                PLATFORM).networkModelRef());
        assertEquals("default", generator.generate("legit-a", BehaviorProfile.WALK, "unknown-scenario", 1L,
                10, PLATFORM).networkModelRef());
    }

    @Test
    void sessionsCarryLegitPlayerType() {
        List<BehaviorTrajectory> cohort = generator.cohort(2, BehaviorProfile.SPRINT, "pvp", 9L, PLATFORM, 30);
        List<SimulatedSession> sessions = new BehaviorSessionFactory().toSessions(cohort);
        assertEquals(2, sessions.size());
        assertTrue(sessions.stream().allMatch(session -> session.playerType() == PlayerType.LEGIT));
        assertTrue(sessions.stream().allMatch(session -> !session.isCheat()));
        assertTrue(sessions.stream().allMatch(session -> "pvp".equals(session.scenarioId())));
        assertTrue(sessions.stream().allMatch(session -> session.stream().size() > 0));
        assertEquals(sessions.get(0).playerId(), sessions.get(0).trajectoryRef());
        assertEquals(cohort.get(0).eventCount(), sessions.get(0).stream().size());
    }
}
