package io.laplace.sprite.sandbox;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 沙箱 spec（15.4）：堆派生比例、等级配额、非法声明拦截。 */
class SandboxSpecTest {

    private static final Path DATA = Path.of("build", "sandbox-test", "data");

    @Test
    void heapIsRoughlySeventyPercentOfContainerMemory() {
        assertEquals(717, SandboxSpec.deriveHeapMb(1024));
        assertEquals(1434, SandboxSpec.deriveHeapMb(2048));
        assertEquals(2150, SandboxSpec.deriveHeapMb(3072));
        double ratio = SandboxSpec.deriveHeapMb(4096) / 4096.0;
        assertTrue(ratio > 0.69 && ratio < 0.71, "堆占比应约 70%，实际 " + ratio);
    }

    @Test
    void tierPresetsFeedQuotaAndBackoffBudget() {
        SandboxSpec smoke = spec(SandboxTier.SMOKE);
        assertEquals(1024, smoke.memoryMb());
        assertEquals(1.0, smoke.cpus());
        assertEquals(717, smoke.jvmHeapMb());
        assertEquals(1, smoke.oomRestartLimit());
        assertEquals(358, smoke.initHeapMb());
        assertTrue(smoke.worldSnapshotEnabled());
        assertEquals(SandboxNetworkPolicy.NETWORK_PREFIX + smoke.caseId(), smoke.networkPolicy().networkName());

        assertEquals(3, spec(SandboxTier.HEAVY).oomRestartLimit());
        assertEquals(8192, spec(SandboxTier.HEAVY).memoryMb());
        assertEquals(2, spec(SandboxTier.STANDARD).oomRestartLimit());
    }

    @Test
    void illegalDeclarationsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> specWithCaseId("Case 1"));
        assertThrows(IllegalArgumentException.class, () -> specWithCaseId(""));
        assertThrows(IllegalArgumentException.class, () -> specWithHeap(1024, 1024));
        assertThrows(IllegalArgumentException.class, () -> withRconPort(80));
        assertThrows(IllegalArgumentException.class, () -> withOomLimit(-1));
        assertThrows(IllegalArgumentException.class, () -> new SandboxSpec("case-1", " ", "PAPER", "1.21.11",
                1024, 1.0, 717, List.of(), DATA, SandboxNetworkPolicy.isolate("case-1"), 25575, true, 1,
                Map.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new SandboxSpec("case-1", "img", "PAPER", "1.21.11",
                1024, 1.0, 717, List.of(new SandboxMount("/host/mods", "/data/mods", false)), DATA,
                SandboxNetworkPolicy.isolate("case-1"), 25575, true, 1, Map.of(), Map.of()),
                "readOnlyMounts 不接受可写挂载");
        assertThrows(IllegalArgumentException.class, () -> new SandboxSpec("case-1", "img", "PAPER", "1.21.11",
                1024, 1.0, 717, List.of(SandboxMount.readOnly("/host/data", "/data")), DATA,
                SandboxNetworkPolicy.isolate("case-1"), 25575, true, 1, Map.of(), Map.of()),
                "/data 由 writableDataDir 独占");
    }

    @Test
    void mountRendersReadOnlySuffix() {
        assertEquals("/host/mods:/data/mods:ro", SandboxMount.readOnly("/host/mods", "/data/mods").volumeArgument());
        assertThrows(IllegalArgumentException.class, () -> SandboxMount.readOnly(" ", "/data/mods"));
    }

    private static SandboxSpec spec(SandboxTier tier) {
        return SandboxSpec.forTier("case-1", tier, "itzg/minecraft-server:java21", "PAPER", "1.21.11",
                List.of(SandboxMount.readOnly("/host/mods", "/data/mods")), DATA, 25575);
    }

    private static SandboxSpec specWithCaseId(String caseId) {
        return SandboxSpec.forTier(caseId, SandboxTier.SMOKE, "img", "PAPER", "1.21.11", List.of(), DATA, 25575);
    }

    private static SandboxSpec specWithHeap(int memoryMb, int heapMb) {
        return new SandboxSpec("case-1", "img", "PAPER", "1.21.11", memoryMb, 1.0, heapMb, List.of(),
                DATA, SandboxNetworkPolicy.isolate("case-1"), 25575, true, 1, Map.of(), Map.of());
    }

    private static SandboxSpec withRconPort(int rconPort) {
        return new SandboxSpec("case-1", "img", "PAPER", "1.21.11", 1024, 1.0, 717, List.of(), DATA,
                SandboxNetworkPolicy.isolate("case-1"), rconPort, true, 1, Map.of(), Map.of());
    }

    private static SandboxSpec withOomLimit(int limit) {
        return new SandboxSpec("case-1", "img", "PAPER", "1.21.11", 1024, 1.0, 717, List.of(), DATA,
                SandboxNetworkPolicy.isolate("case-1"), 25575, true, limit, Map.of(), Map.of());
    }
}
