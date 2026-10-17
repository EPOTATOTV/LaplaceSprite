package io.laplace.sprite.cheats;

import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.model.NetworkModel;
import io.laplace.sprite.core.model.SampleTier;
import io.laplace.sprite.core.model.SampleTierName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 通用异常注入器：四档都要改变基线、不改写基线，且同 seed 结果稳定。 */
class AnomalyInjectorBehaviourTest {

    private static final long SEED = 42L;

    private final CheatCatalog catalog = new CheatCatalog();
    private final CheatInjectorRegistry registry = new CheatInjectorRegistry();

    private List<SampleSpec> nonCheatSamples() {
        return catalog.allSamples().stream().filter(spec -> !spec.isCheat()).toList();
    }

    @Test
    void everyTierChangesBaselineWithoutMutatingIt() {
        List<SampleSpec> specs = nonCheatSamples();
        assertTrue(specs.size() >= 12, "通用异常样本不足: " + specs.size());
        for (SampleSpec spec : specs) {
            for (SampleTierName tier : SampleTierName.values()) {
                EventStream baseline = TestStreams.walk();
                List<Event> before = baseline.events();
                SampleTier declared = spec.tier(tier).orElseThrow();
                EventStream injected = registry.forSpec(spec).inject(request(spec, tier, baseline));
                assertNotEquals(before, injected.events(), "注入未改变基线: " + spec.id() + "/" + tier);
                assertFalse(injected.isEmpty(), spec.id() + "/" + tier);
                assertEquals(before.size(), baseline.size(), "基线被注入器修改: " + spec.id());
                assertTrue(declared.expected() != null && !declared.expected().expectedDetected(),
                        spec.id() + "/" + tier);
            }
        }
    }

    @Test
    void injectionIsDeterministicPerSeed() {
        for (SampleSpec spec : nonCheatSamples()) {
            EventStream first = registry.forSpec(spec)
                    .inject(request(spec, SampleTierName.DEFAULT, TestStreams.walk()));
            EventStream second = registry.forSpec(spec)
                    .inject(request(spec, SampleTierName.DEFAULT, TestStreams.walk()));
            assertEquals(first.events(), second.events(), spec.id());
        }
    }

    @Test
    void registryCoversEveryDeclaredInjector() {
        for (SampleSpec spec : catalog.allSamples()) {
            assertTrue(registry.supportedIds().contains(spec.injector()), spec.id());
            assertTrue(registry.supportedIds().contains(registry.forSpec(spec).id()), spec.id());
        }
    }

    private static CheatInjectionRequest request(SampleSpec spec, SampleTierName tier, EventStream baseline) {
        return new CheatInjectionRequest(spec, tier, "SimID-" + spec.id(), TestStreams.PLATFORM, baseline,
                TestStreams.scenario(), NetworkModel.defaults(), SEED);
    }
}
