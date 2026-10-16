package io.laplace.sprite.cheats;

import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.event.EventType;
import io.laplace.sprite.core.model.CheatSpec;
import io.laplace.sprite.core.model.CheatTier;
import io.laplace.sprite.core.model.CheatTierName;
import io.laplace.sprite.core.model.NetworkModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 每个注入器在默认档位都要能改变合法基线，且同 seed 结果稳定。 */
class CheatInjectorBehaviourTest {

    private static final long SEED = 42L;

    private final CheatCatalog catalog = new CheatCatalog();
    private final CheatInjectorRegistry registry = new CheatInjectorRegistry();

    @Test
    void defaultTierInjectionChangesBaseline() {
        for (CheatSpec spec : catalog.loadDefaults()) {
            EventStream baseline = TestStreams.walk();
            List<Event> before = baseline.events();
            EventStream injected = registry.of(spec.type())
                    .inject(request(spec, CheatTierName.DEFAULT, baseline, SEED));
            assertNotEquals(before, injected.events(), "注入未改变基线: " + spec.id());
            assertFalse(injected.isEmpty(), spec.id());
            assertEquals(before.size(), baseline.size(), "基线被注入器修改: " + spec.id());
        }
    }

    @Test
    void appendedEventsCarryRequestedPlayerId() {
        CheatSpec spec = catalog.byId("killaura.wurst.default").orElseThrow();
        EventStream injected = registry.of(spec.type())
                .inject(request(spec, CheatTierName.DEFAULT, TestStreams.walk(), SEED));
        assertTrue(injected.events().stream()
                .anyMatch(event -> "SimID-killaura.wurst.default".equals(event.playerId())));
    }

    @Test
    void hiddenTierSpoofsIdentityDeterministically() {
        CheatSpec spec = catalog.byId("speed.meteor.hidden").orElseThrow();
        EventStream first = registry.of(spec.type())
                .inject(request(spec, CheatTierName.HIDDEN, TestStreams.walk(), SEED));
        EventStream second = registry.of(spec.type())
                .inject(request(spec, CheatTierName.HIDDEN, TestStreams.walk(), SEED));
        assertEquals(first.events(), second.events());
        assertEquals(3, first.events().stream()
                .filter(event -> event.type() == EventType.BRAND
                        || event.type() == EventType.MOD_HASH
                        || event.type() == EventType.ADDON)
                .count());
    }

    private static CheatInjectionRequest request(CheatSpec spec, CheatTierName tierName, EventStream baseline,
                                                 long seed) {
        CheatTier tier = spec.tier(tierName).orElseThrow();
        assertTrue(tier.expected().expectedDetected(), spec.id());
        return new CheatInjectionRequest(spec, tierName, "SimID-" + spec.id(), TestStreams.PLATFORM, baseline,
                TestStreams.scenario(), NetworkModel.defaults(), seed);
    }
}
