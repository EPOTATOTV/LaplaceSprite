package io.laplace.sprite.probes;

import io.laplace.sprite.core.model.ProbeCapabilities;
import io.laplace.sprite.core.model.ProbeSnapshot;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessScannerStubTest {

    private final ProcessScannerStub scanner = new ProcessScannerStub();

    @Test
    void fingerprintLibraryIsLargeEnough() {
        assertTrue(scanner.fingerprintCount() >= 80, "指纹数不足: " + scanner.fingerprintCount());
        assertEquals(scanner.fingerprints().size(), scanner.fingerprintCount());
    }

    @Test
    void cheatProcessNamesArePresent() {
        assertFalse(scanner.cheatProcessNames().isEmpty());
        assertTrue(scanner.cheatProcessNames().size() >= 5);
    }

    @Test
    void cleanSnapshotIsNotInjected() {
        assertFalse(scanner.looksInjected(ProbeSnapshot.clean()));
    }

    @Test
    void fullSnapshotsDeclareEveryDimensionSupported() {
        ProbeSnapshot clean = ProbeSnapshot.clean();
        assertTrue(clean.unsupportedCapabilities().isEmpty(), "全维快照不应有查不了的维度");
        assertTrue(scanner.detectionIsComplete(clean));
        assertEquals(ProbeCapabilities.values().length, scanner.evaluableCapabilities().size());
    }

    @Test
    void unsupportedDimensionIsReportedNotSilentlyTreatedAsClean() {
        ProbeSnapshot degraded = new ProbeSnapshot(List.of("javaw.exe"), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), Map.of(),
                capabilitiesWithout(ProbeCapabilities.USB, ProbeCapabilities.REGISTRY));
        assertEquals(List.of(ProbeCapabilities.USB, ProbeCapabilities.REGISTRY),
                scanner.unsupportedDimensions(degraded));
        assertFalse(scanner.detectionIsComplete(degraded), "有维度查不了时判定不完整");
        assertFalse(scanner.looksInjected(degraded), "查不了不等于命中");
    }

    @Test
    void markerInUnsupportedDimensionStaysInconclusive() {
        ProbeSnapshot snapshot = new ProbeSnapshot(List.of("javaw.exe"),
                List.of("wurst-client.jar"), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), Map.of(), capabilitiesWithout(ProbeCapabilities.MODULES));
        // 模块维度查不了：里面的内容不作为命中证据，但也不能被当成清白。
        assertFalse(scanner.looksInjected(snapshot));
        assertFalse(scanner.detectionIsComplete(snapshot));
        assertEquals(List.of(ProbeCapabilities.MODULES), scanner.unsupportedDimensions(snapshot));
    }

    private static Map<String, Boolean> capabilitiesWithout(ProbeCapabilities... unsupported) {
        Map<String, Boolean> capabilities = new LinkedHashMap<>(ProbeSnapshot.fullCapabilities());
        for (ProbeCapabilities capability : unsupported) {
            capabilities.put(capability.key(), false);
        }
        return capabilities;
    }
}
