package io.laplace.sprite.simulator;

import io.laplace.sprite.core.model.BehaviorProfile;
import io.laplace.sprite.core.model.Scenario;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationProfileTest {

    @Test
    void mixMatchesScenarioPlayerCount() {
        SimulationProfile profile = SimulationProfile.of("pvp");
        Scenario scenario = new ScenarioLibrary().defaultScenario();
        int total = profile.mix().values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(scenario.simulatedPlayers(), total);
        assertTrue(profile.mix().keySet().stream().allMatch(scenario.profiles()::contains));
    }

    @Test
    void rotationSpreadsScenarioProfiles() {
        SimulationProfile profile = SimulationProfile.of("low-tps");
        Scenario scenario = new ScenarioLibrary().byId("low-tps").orElseThrow();
        List<BehaviorProfile> rotation = profile.rotation(12, 7L);
        assertEquals(12, rotation.size());
        assertTrue(scenario.profiles().containsAll(rotation));
    }

    @Test
    void rotationIsDeterministicAndUnknownScenarioFallsBack() {
        SimulationProfile profile = SimulationProfile.of("low-tps");
        assertEquals(profile.rotation(12, 7L), profile.rotation(12, 7L));
        assertEquals("pvp", SimulationProfile.of("missing-scenario").scenarioId());
    }
}
