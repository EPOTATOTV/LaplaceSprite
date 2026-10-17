package io.laplace.sprite.adapters;

import io.laplace.sprite.adapters.sim.HeuristicDetectionEngine;
import io.laplace.sprite.adapters.sim.SimServerAdapter;
import io.laplace.sprite.cheats.CheatCatalog;
import io.laplace.sprite.cheats.CheatSessionFactory;
import io.laplace.sprite.core.engine.DetectionContext;
import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.engine.SimulatedSession;
import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.model.BehaviorProfile;
import io.laplace.sprite.core.model.TestSpec;
import io.laplace.sprite.core.model.SampleTierName;
import io.laplace.sprite.core.model.CheatType;
import io.laplace.sprite.core.model.NetworkModel;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.model.PlayerType;
import io.laplace.sprite.core.model.ProbeSnapshot;
import io.laplace.sprite.core.model.Scenario;
import io.laplace.sprite.network.NetworkModelRegistry;
import io.laplace.sprite.simulator.BehaviorTrajectoryGenerator;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验收回归（4.1 / 4.5 / 9 验收 1）：把 CLI 的三条门禁固化在 adapters 内进程轨上。
 *
 * <p>用固定种子拉起真实的行为模型与作弊注入，断言合法零误报、DEFAULT 档检出率 ≥95%、边界样本全通过。
 * 数据全部来自模拟器与 cheats 模块，检测引擎看不到答案（{@code DetectionContext} 里的
 * {@code cheatSpec}/{@code tier} 只用于标注，不参与判定）。</p>
 */
class DetectionAcceptanceTest {

    private static final PlatformKey PLATFORM = PlatformKey.of(PlatformFamily.PAPER, "1.21.11");
    private static final long SEED = 20261013L;
    private static final int TICKS = 200;
    private static final int PROFILES_PER_COHORT = 50;
    private static final double MIN_DETECTION_RATE = 0.95;

    private final SimServerAdapter adapter = new SimServerAdapter(new HeuristicDetectionEngine());
    private final BehaviorTrajectoryGenerator generator = new BehaviorTrajectoryGenerator();
    private final Scenario scenario = new Scenario("pvp", "pvp", List.of(), "default", 8, Map.of());

    @Test
    void legitCohortAcrossEveryProfileProducesZeroFlags() {
        int flagged = 0;
        for (BehaviorProfile profile : BehaviorProfile.values()) {
            for (int i = 0; i < PROFILES_PER_COHORT; i++) {
                EventStream stream = new InMemoryEventStream(generator
                        .generate(profile.name().toLowerCase(java.util.Locale.ROOT) + "-" + i, profile, "pvp",
                                SEED + i, TICKS, PLATFORM).events());
                flagged += adapter.detect(ctx(stream, profile.name() + "-" + i, PlayerType.LEGIT, null, null))
                        .size();
            }
        }
        assertEquals(0, flagged, "合法行为模型不得产生误报");
    }

    @Test
    void cheatSamplesAtDefaultTierAreDetectedAboveThreshold() {
        List<TestSpec> specs = new CheatCatalog().loadDefaults();
        List<SimulatedSession> sessions = new CheatSessionFactory()
                .buildSessions(specs, SampleTierName.DEFAULT, "pvp", PLATFORM, network(), SEED, baseline());
        int detected = 0;
        Set<CheatType> covered = EnumSet.noneOf(CheatType.class);
        for (SimulatedSession session : sessions) {
            if (!adapter.detect(ctx(session.stream(), session.playerId(), PlayerType.CHEAT,
                    session.cheatSpec(), session.tier())).isEmpty()) {
                detected++;
                covered.add(session.cheatSpec().type());
            }
        }
        assertFalse(sessions.isEmpty(), "作弊样本集不得为空");
        double rate = (double) detected / sessions.size();
        assertTrue(rate >= MIN_DETECTION_RATE,
                "DEFAULT 档检出率 %.4f 低于 %.2f（%d/%d）".formatted(rate, MIN_DETECTION_RATE, detected,
                        sessions.size()));
        assertEquals(Set.of(CheatType.values()), covered, "存在未被任何规则覆盖的作弊类型");
    }

    @Test
    void boundarySamplesAllMatchExpectation() {
        List<TestSpec> specs = new CheatCatalog().loadDefaults();
        List<SimulatedSession> sessions = new CheatSessionFactory()
                .buildBoundarySessions(specs, "pvp", PLATFORM, network(), SEED, baseline());
        int passed = 0;
        for (SimulatedSession session : sessions) {
            boolean hit = !adapter.detect(ctx(session.stream(), session.playerId(), PlayerType.CHEAT,
                    session.cheatSpec(), session.tier())).isEmpty();
            boolean expected = session.expected() != null && session.expected().expectedDetected();
            if (hit == expected) {
                passed++;
            }
        }
        assertFalse(sessions.isEmpty(), "边界样本集不得为空");
        assertEquals(sessions.size(), passed, "阈值边界样本必须全部符合预期");
    }

    private EventStream baseline() {
        return new InMemoryEventStream(generator
                .generate("baseline", BehaviorProfile.SPRINT, "pvp", SEED, TICKS, PLATFORM).events());
    }

    private static NetworkModel network() {
        return new NetworkModelRegistry().defaultModel();
    }

    private DetectionContext ctx(EventStream stream, String playerId, PlayerType playerType, TestSpec spec,
                                 SampleTierName tier) {
        return new DetectionContext("acceptance", PLATFORM, scenario, stream, ProbeSnapshot.clean(), playerId,
                playerType, spec, tier);
    }
}
