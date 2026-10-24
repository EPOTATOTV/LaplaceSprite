package io.laplace.sprite.adapters;

import io.laplace.sprite.core.spi.AdapterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第三方适配器仅靠 META-INF/services 注入，内核与工厂无需硬编码。 */
class AdapterProviderInjectionTest {

    @Test
    void providerIsDiscoveredAndItsAdaptersRegistered() {
        List<String> providedIds = AdapterRegistryFactory.providerAdapters().stream()
                .map(adapter -> adapter.id())
                .toList();
        assertEquals(List.of("test-provider", "paper"), providedIds);

        AdapterRegistry registry = AdapterRegistryFactory.defaultRegistry();

        assertTrue(registry.byId("test-provider").isPresent());
        assertEquals(25 + 1, registry.size(), "冲突 id 应被跳过");
    }

    @Test
    void duplicateIdIsSkippedWithWarning() {
        AdapterRegistry registry = AdapterRegistryFactory.defaultRegistry();

        assertTrue(registry.warnings().stream()
                        .anyMatch(warning -> warning.contains("重复") && warning.contains("paper")),
                registry.warnings().toString());
    }
}
