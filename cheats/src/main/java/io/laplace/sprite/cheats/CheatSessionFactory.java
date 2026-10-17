package io.laplace.sprite.cheats;

import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.engine.SimulatedSession;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.model.TestSpec;
import io.laplace.sprite.core.model.SampleTier;
import io.laplace.sprite.core.model.SampleTierName;
import io.laplace.sprite.core.model.ExpectedDetection;
import io.laplace.sprite.core.model.NetworkModel;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.model.ProbeSnapshot;
import io.laplace.sprite.core.model.Scenario;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 把作弊样本 × 档位组装成待执行会话（输入层）。
 * 注入器只读基线，因此所有会话可共用同一份合法基线。
 */
public final class CheatSessionFactory {

    private static final long TICK_MILLIS = 50L;
    private static final int SYNTHETIC_TICKS = 40;

    private final CheatInjectorRegistry registry;

    public CheatSessionFactory() {
        this(new CheatInjectorRegistry());
    }

    public CheatSessionFactory(CheatInjectorRegistry registry) {
        this.registry = registry;
    }

    /** 每个 (样本, 档位) 组合一个会话；样本未声明该档位时跳过。 */
    public List<SimulatedSession> buildSessions(List<TestSpec> specs, SampleTierName tier, String scenarioId,
                                                PlatformKey platform, NetworkModel network, long seed,
                                                EventStream baselineTemplate) {
        EventStream baseline = baselineOrSynthetic(baselineTemplate, platform);
        Scenario scenario = scenarioOf(scenarioId, network);
        List<SimulatedSession> sessions = new ArrayList<>();
        for (TestSpec spec : specs) {
            Optional<SampleTier> declared = spec.tier(tier);
            if (declared.isEmpty()) {
                continue;
            }
            sessions.add(build(spec, tier, scenarioId, platform, network, seed, baseline, scenario,
                    declared.get()));
        }
        return List.copyOf(sessions);
    }

    /** 每个样本只取阈值边界档位；样本未标注边界时，用默认档位参数合成边界预期。 */
    public List<SimulatedSession> buildBoundarySessions(List<TestSpec> specs, String scenarioId,
                                                        PlatformKey platform, NetworkModel network, long seed,
                                                        EventStream baselineTemplate) {
        EventStream baseline = baselineOrSynthetic(baselineTemplate, platform);
        Scenario scenario = scenarioOf(scenarioId, network);
        List<SimulatedSession> sessions = new ArrayList<>();
        for (TestSpec spec : specs) {
            Optional<SampleTier> boundary = spec.tiers().stream()
                    .filter(declared -> declared.expected() != null && declared.expected().boundary())
                    .findFirst();
            TestSpec effectiveSpec = spec;
            SampleTier effectiveTier;
            if (boundary.isPresent()) {
                effectiveTier = boundary.get();
            } else {
                Optional<SampleTier> fallback = spec.tier(SampleTierName.DEFAULT)
                        .or(() -> spec.tiers().stream().findFirst());
                if (fallback.isEmpty()) {
                    continue;
                }
                effectiveTier = new SampleTier(fallback.get().tier(), fallback.get().params(),
                        ExpectedDetection.boundarySample());
                effectiveSpec = replaceTier(spec, effectiveTier);
            }
            sessions.add(build(effectiveSpec, effectiveTier.tier(), scenarioId, platform, network, seed, baseline,
                    scenario, effectiveTier));
        }
        return List.copyOf(sessions);
    }

    private SimulatedSession build(TestSpec spec, SampleTierName tierName, String scenarioId, PlatformKey platform,
                                   NetworkModel network, long seed, EventStream baseline, Scenario scenario,
                                   SampleTier tier) {
        String playerId = playerId(spec, tierName);
        CheatInjectionRequest request = new CheatInjectionRequest(spec, tierName, playerId, platform, baseline,
                scenario, network, seed);
        EventStream injected = registry.of(spec.type()).inject(request);
        return SimulatedSession.cheat(playerId, spec, tierName, scenarioId, injected, probeOf(spec),
                trajectoryRef(spec), tier.expected());
    }

    private static TestSpec replaceTier(TestSpec spec, SampleTier replacement) {
        List<SampleTier> tiers = spec.tiers().stream()
                .map(tier -> tier.tier() == replacement.tier() ? replacement : tier)
                .toList();
        return new TestSpec(spec.id(), spec.name(), spec.type(), spec.target(), tiers, spec.baseline(),
                spec.injector(), spec.source(), spec.notes());
    }

    private static String playerId(TestSpec spec, SampleTierName tier) {
        return "SimID-" + spec.id() + "-" + tier.name().toLowerCase(Locale.ROOT);
    }

    private static String trajectoryRef(TestSpec spec) {
        return spec.baseline() == null ? "baseline:vanilla" : spec.baseline();
    }

    private static ProbeSnapshot probeOf(TestSpec spec) {
        String client = spec.source() == null ? "unknown" : spec.source().toLowerCase(Locale.ROOT);
        return new ProbeSnapshot(List.of("javaw.exe", "minecraft.exe"), List.of(client), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), Map.of("injector", String.valueOf(spec.injector())));
    }

    private static Scenario scenarioOf(String scenarioId, NetworkModel network) {
        return new Scenario(scenarioId, scenarioId, List.of(), network == null ? null : network.id(), 1, Map.of());
    }

    private static EventStream baselineOrSynthetic(EventStream baselineTemplate, PlatformKey platform) {
        return baselineTemplate != null ? baselineTemplate : syntheticBaseline(platform);
    }

    /** 无合法基线时补一段原版步行轨迹，仅保证注入有事件载体。 */
    private static EventStream syntheticBaseline(PlatformKey platform) {
        InMemoryEventStream stream = new InMemoryEventStream();
        Instant origin = Instant.parse("2026-01-01T00:00:00Z");
        String playerId = "SimID-baseline";
        double x = 0.5;
        for (int tick = 0; tick < SYNTHETIC_TICKS; tick++) {
            Instant timestamp = origin.plusMillis(tick * TICK_MILLIS);
            stream.emit(new Event.Move(playerId, tick, timestamp, platform, x, 64.0, 0.5, 0f, 0f, true, true,
                    false));
            if (tick % 5 == 0) {
                stream.emit(new Event.Packet(playerId, tick, timestamp, platform, "SERVERBOUND", "MOVE", 32));
            }
            stream.emit(new Event.Tick(playerId, tick, timestamp, platform, 20.0, 3.5));
            x += 0.215;
        }
        return stream;
    }
}
