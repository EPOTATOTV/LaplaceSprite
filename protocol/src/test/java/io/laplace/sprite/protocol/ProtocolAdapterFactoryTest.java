package io.laplace.sprite.protocol;

import io.laplace.sprite.adapters.AdapterStatus;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.spi.AdapterRegistry;
import io.laplace.sprite.core.spi.LaplaceAdapter;
import io.laplace.sprite.protocol.adapter.ViaProxyAdapter;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 协议轨适配器（15.6 归属）：作为普通代理端适配器对外暴露。 */
class ProtocolAdapterFactoryTest {

    @Test
    void exposesViaProxyAdapter() {
        ViaProxyAdapter adapter = ProtocolAdapterFactory.viaProxy();

        assertEquals("viaproxy", adapter.id());
        assertEquals(PlatformFamily.VELOCITY, adapter.family());
        assertTrue(adapter.family().isProxy());
        assertEquals(AdapterStatus.IMPLEMENTED, adapter.adapterStatus());
        assertEquals("viaproxy.yml", adapter.descriptorFile());
        assertEquals(1, ProtocolAdapterFactory.adapters().size());
        assertEquals("viaproxy", ProtocolAdapterFactory.adapters().get(0).id());
        assertEquals("viaproxy", adapter.toString());
    }

    @Test
    void adapterRegistersIntoCoreRegistry() {
        AdapterRegistry registry = new AdapterRegistry();
        ProtocolAdapterFactory.adapters().forEach(registry::register);

        Optional<LaplaceAdapter> resolved =
                registry.resolve(PlatformKey.of(PlatformFamily.VELOCITY, "1.21.1"));
        assertTrue(resolved.isPresent());
        assertEquals("viaproxy", resolved.orElseThrow().id());
        assertTrue(registry.warnings().isEmpty(), "精确命中不该出降级告警");
    }

    @Test
    void adapterReusesProxyInstallLayoutAndEventBridge() {
        ViaProxyAdapter adapter = ProtocolAdapterFactory.viaProxy();
        PlatformKey key = PlatformKey.of(PlatformFamily.VELOCITY, "1.21.1");

        assertEquals(Path.of("server", "plugins"), adapter.installLayout().layoutDirectory(key));
        assertNotNull(adapter.eventBridge().bridge(key));
        assertNotNull(adapter.assertionContract());
        assertEquals(3, adapter.smokeScenarios().size());
        assertTrue(adapter.testAdaptationNotes().size() >= 3);
    }
}
