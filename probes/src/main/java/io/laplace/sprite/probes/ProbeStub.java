package io.laplace.sprite.probes;

import io.laplace.sprite.core.model.ProbeSnapshot;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 探针桩入口（4.2 玩家端样本）：按 profile 注入对应的 {@link ProbeSnapshot}。
 */
public final class ProbeStub {

    private static final String BASELINE = "baseline";
    private static final String HARDWARE_CHEAT = "hardware-cheat";
    private static final String DEGRADED_LAB = "degraded-lab";

    private final ProbeSampleLibrary library;

    public ProbeStub(ProbeSampleLibrary library) {
        this.library = library;
    }

    /** 基线快照。 */
    public ProbeSnapshot snapshot() {
        return library.baseline();
    }

    /**
     * 按 profile 取快照：{@code baseline} / 各客户端名 / {@code hardware-cheat} / {@code degraded-lab}。
     * {@code degraded-lab} 是锁定环境，部分维度查不了，判定结果不完整。
     */
    public ProbeSnapshot applyProfile(String profileId) {
        if (BASELINE.equals(profileId)) {
            return library.baseline();
        }
        if (DEGRADED_LAB.equals(profileId)) {
            return library.degradedSample();
        }
        List<ProbeSnapshot> samples = library.byClient(profileId);
        if (samples.isEmpty()) {
            throw new IllegalArgumentException("未知 profile: " + profileId + "，可用: " + profiles());
        }
        return samples.get(0);
    }

    public Set<String> profiles() {
        Set<String> profiles = new LinkedHashSet<>();
        profiles.add(BASELINE);
        profiles.addAll(library.sampleCounts().keySet());
        profiles.add(HARDWARE_CHEAT);
        profiles.add(DEGRADED_LAB);
        return profiles;
    }
}
