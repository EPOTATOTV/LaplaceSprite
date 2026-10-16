package io.laplace.sprite.probes;

import io.laplace.sprite.core.model.ProbeCapabilities;
import io.laplace.sprite.core.model.ProbeSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProbeSampleLibraryTest {

    private final ProcessScannerStub scanner = new ProcessScannerStub();
    private final CheatClientSignatures signatures = new CheatClientSignatures();
    private final ProbeSampleLibrary library = new ProbeSampleLibrary();

    @Test
    void everyClientHasPositiveSample() {
        for (String client : signatures.clients()) {
            assertFalse(library.byClient(client).isEmpty(), client + " 缺少正例");
        }
        assertFalse(library.byClient("hardware-cheat").isEmpty(), "缺少硬件外挂正例");
    }

    @Test
    void positiveSamplesAreInjected() {
        assertFalse(library.positiveSamples().isEmpty());
        for (ProbeSnapshot sample : library.positiveSamples()) {
            assertTrue(scanner.looksInjected(sample), "正例未被判定为注入");
        }
    }

    @Test
    void negativeSamplesAreClean() {
        assertFalse(library.negativeSamples().isEmpty());
        for (ProbeSnapshot sample : library.negativeSamples()) {
            assertFalse(scanner.looksInjected(sample), "反例被误判为注入");
        }
    }

    @Test
    void hardwareCheatUsesMcuDevice() {
        assertTrue(library.byClient("hardware-cheat").stream()
                .flatMap(sample -> sample.usbDevices().stream())
                .anyMatch(device -> device.mcu()), "硬件外挂样本缺少 MCU 设备");
    }

    @Test
    void sampleCountsCoverAllProfiles() {
        assertTrue(library.sampleCounts().keySet().containsAll(signatures.clients()));
        assertTrue(library.sampleCounts().containsKey("hardware-cheat"));
    }

    @Test
    void probeStubProfilesCoverClients() {
        ProbeStub stub = new ProbeStub(library);
        assertTrue(stub.profiles().contains("baseline"));
        assertTrue(stub.profiles().contains("hardware-cheat"));
        assertTrue(stub.profiles().contains("degraded-lab"));
        for (String client : signatures.clients()) {
            assertTrue(stub.profiles().contains(client), "profile 缺少客户端: " + client);
            assertTrue(scanner.looksInjected(stub.applyProfile(client)));
        }
        assertFalse(scanner.looksInjected(stub.snapshot()));
        assertFalse(scanner.detectionIsComplete(stub.applyProfile("degraded-lab")));
    }

    @Test
    void fullSnapshotsDeclareEveryDimensionSupported() {
        assertTrue(library.baseline().unsupportedCapabilities().isEmpty());
        for (ProbeSnapshot sample : library.positiveSamples()) {
            assertTrue(sample.unsupportedCapabilities().isEmpty(), "正例不应有查不了的维度");
            assertTrue(scanner.detectionIsComplete(sample));
        }
    }

    @Test
    void degradedSampleMarksDimensionsUnsupported() {
        ProbeSnapshot degraded = library.degradedSample();
        assertEquals(List.of(ProbeCapabilities.USB, ProbeCapabilities.REGISTRY),
                degraded.unsupportedCapabilities());
        assertTrue(library.negativeSamples().contains(degraded), "退化样本应作为反例参与验证");
        assertFalse(scanner.looksInjected(degraded), "退化样本自身没有痕迹");
        assertFalse(scanner.detectionIsComplete(degraded), "查不了的维度不能算清白");
    }
}
