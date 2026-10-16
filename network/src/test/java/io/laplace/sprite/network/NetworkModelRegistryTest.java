package io.laplace.sprite.network;

import io.laplace.sprite.core.model.NetworkModel;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkModelRegistryTest {

    private final NetworkModelRegistry registry = new NetworkModelRegistry();

    @Test
    void builtInCoversRequiredModels() {
        Set<String> ids = registry.builtIn().stream().map(NetworkModel::id).collect(Collectors.toSet());
        assertTrue(ids.containsAll(Set.of("default", "wifi", "lte", "fixed-100ms", "spike", "lossy",
                NetworkModelRegistry.BEDROCK_ID)), "缺少网络模型: " + ids);
    }

    @Test
    void specialModelsCarryExpectedParameters() {
        NetworkModel fixed = registry.byId("fixed-100ms").orElseThrow();
        assertEquals(fixed.latencyMinMs(), fixed.latencyMaxMs());
        assertTrue(registry.byId("spike").orElseThrow().spikeMs() > 0);

        NetworkModel lossy = registry.byId("lossy").orElseThrow();
        assertTrue(lossy.packetLossRate() > 0);
        assertTrue(lossy.reorderRate() > 0);

        assertTrue(registry.byId(NetworkModelRegistry.BEDROCK_ID).orElseThrow().clientVersion()
                .startsWith("bedrock"));
    }

    @Test
    void defaultLookupAndSeedOverride() {
        assertEquals("default", registry.defaultModel().id());
        assertTrue(registry.byId("missing").isEmpty());

        NetworkModel base = NetworkModel.wifi();
        NetworkModel seeded = registry.withSeed(base, 99L);
        assertEquals(99L, seeded.seed());
        assertEquals(base.latencyMinMs(), seeded.latencyMinMs());
        assertEquals(base.latencyMaxMs(), seeded.latencyMaxMs());
        assertEquals(base.packetLossRate(), seeded.packetLossRate());
        assertEquals(base.batchPattern(), seeded.batchPattern());
        assertEquals(base.clientVersion(), seeded.clientVersion());
    }
}
