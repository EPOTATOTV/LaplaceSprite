package io.laplace.sprite.simulator;

import io.laplace.sprite.core.model.Scenario;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioLibraryTest {

    private static final List<String> REQUIRED_IDS = List.of("pvp", "mining", "bridge-sprint", "reconnect",
            "respawn", "warmup", "low-tps");

    private final ScenarioLibrary library = new ScenarioLibrary();

    @Test
    void builtInCoversRequiredScenarios() {
        Set<String> ids = library.builtIn().stream().map(Scenario::id).collect(Collectors.toSet());
        assertTrue(ids.containsAll(REQUIRED_IDS), "缺少剧本: " + ids);
        assertEquals(REQUIRED_IDS.size(), library.builtIn().size());
    }

    @Test
    void scenariosCarryProfilesNetworkRefAndSteps() {
        for (Scenario scenario : library.builtIn()) {
            assertFalse(scenario.name().isBlank(), scenario.id() + " 缺少展示名");
            assertFalse(scenario.profiles().isEmpty(), scenario.id() + " 缺少行为模型");
            assertTrue(List.of("default", "wifi", "lte").contains(scenario.networkModelRef()),
                    scenario.id() + " 网络模型引用非法");
            assertTrue(scenario.simulatedPlayers() > 0, scenario.id() + " 玩家数非法");
            assertFalse(scenario.steps().isEmpty(), scenario.id() + " 缺少步骤");
        }
    }

    @Test
    void byIdAndDefaultScenario() {
        assertTrue(library.byId("mining").isPresent());
        assertTrue(library.byId("not-exist").isEmpty());
        assertEquals("pvp", library.defaultScenario().id());
        assertEquals("竞技对抗", library.defaultScenario().name());
    }
}
