package io.laplace.sprite.core.engine;

import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.model.BehaviorProfile;
import io.laplace.sprite.core.model.ExemptionRule;
import io.laplace.sprite.core.model.FlagEvent;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.model.Scenario;
import io.laplace.sprite.core.model.SessionContext;
import io.laplace.sprite.core.model.TestRun;
import io.laplace.sprite.core.model.Thresholds;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 4.1 豁免规则：创造 / 旁观 / 允许飞行 / 开局缓冲期 / 插件传送与击退不计误报。
 *
 * <p>豁免只影响误报率口径，flag 本身仍然进证据链。</p>
 */
class ExemptionPolicyTest {

    private static final Scenario PVP = new Scenario("pvp", "竞技对枪", List.of(BehaviorProfile.SPRINT),
            "default", 2, Map.of());

    @Test
    void defaultsComeFromThePluginConfig() {
        ExemptionPolicy policy = ExemptionPolicy.defaults();

        assertEquals(Set.of(ExemptionRule.CREATIVE, ExemptionRule.SPECTATOR,
                ExemptionRule.ALLOW_FLIGHT, ExemptionRule.JOIN_GRACE), policy.enabled());
        assertEquals(ExemptionPolicy.DEFAULT_JOIN_GRACE_TICKS, policy.joinGraceTicks());
    }

    @Test
    void creativeAndSpectatorAreExempted() {
        ExemptionPolicy policy = ExemptionPolicy.defaults();

        assertEquals(ExemptionRule.CREATIVE,
                policy.exemptionFor(legit().withContext(SessionContext.creative()), 10).orElseThrow());
        assertEquals(ExemptionRule.SPECTATOR,
                policy.exemptionFor(legit().withContext(SessionContext.spectator()), 10).orElseThrow());
    }

    @Test
    void plainSurvivalIsNotExempted() {
        assertTrue(ExemptionPolicy.defaults().exemptionFor(legit(), 10).isEmpty());
    }

    @Test
    void joinGraceWindowIsInclusiveAtTheBoundary() {
        ExemptionPolicy policy = new ExemptionPolicy(Set.of(ExemptionRule.JOIN_GRACE), 100);

        assertTrue(policy.exempts(sessionWithJoinAge(100), 10));
        assertFalse(policy.exempts(sessionWithJoinAge(101), 10));
    }

    @Test
    void pluginTransportCoversTheTicksRightAfterIt() {
        ExemptionPolicy policy = new ExemptionPolicy(
                Set.of(ExemptionRule.PLUGIN_TELEPORT, ExemptionRule.PLUGIN_KNOCKBACK), 0);
        SessionContext ctx = new SessionContext(io.laplace.sprite.core.model.GameMode.SURVIVAL, false,
                Long.MAX_VALUE, Set.of(200L), Set.of(400L));
        var session = legit().withContext(ctx);

        assertEquals(ExemptionRule.PLUGIN_TELEPORT, policy.exemptionFor(session, 205).orElseThrow());
        assertEquals(ExemptionRule.PLUGIN_KNOCKBACK, policy.exemptionFor(session, 400).orElseThrow());
        assertTrue(policy.exemptionFor(session, 200 + ExemptionPolicy.PLUGIN_EFFECT_WINDOW_TICKS + 1)
                .isEmpty());
        assertTrue(policy.exemptionFor(session, 199).isEmpty());
    }

    @Test
    void noPolicyNeverExempts() {
        ExemptionPolicy policy = ExemptionPolicy.none();
        var creative = legit().withContext(SessionContext.creative());

        assertTrue(policy.enabled().isEmpty());
        assertTrue(policy.exemptionFor(creative, 10).isEmpty());
    }

    @Test
    void unknownRuleNamesAreIgnoredInsteadOfWidening() {
        ExemptionPolicy policy = ExemptionPolicy.parse("CREATIVE,NOT_A_RULE,", 0);

        assertEquals(Set.of(ExemptionRule.CREATIVE), policy.enabled());
    }

    @Test
    void runnerExemptsCreativeFlagsButStillCountsSurvivalOnes() {
        RunRequest request = new RunRequest("run-ex", "detection-engine", "legit",
                (PlatformKey) null, 7L, Thresholds.defaults(), false, 0.0,
                ExemptionPolicy.defaults());
        SimulatedSession creative = legit("p1").withContext(SessionContext.creative());
        SimulatedSession survival = legit("p2");

        TestRun run = new Runner().execute(request, PVP, List.of(creative, survival), alwaysFlags());

        assertEquals(2, run.flags().size(), "两条触发都应保留在证据链里");
        assertEquals(1, run.metrics().falsePositives(), "只有生存模式那条计入误报率");
        assertEquals(1, run.exemptedFlags().size());
        assertEquals(ExemptionRule.CREATIVE, run.exemptedFlags().get(0).exemption());
        assertTrue(run.detections().isEmpty(), "被豁免的合规触发不算检出");
    }

    @Test
    void disablingExemptionsCountsBothAsFalsePositives() {
        RunRequest request = new RunRequest("run-none", "detection-engine", "legit",
                (PlatformKey) null, 7L, Thresholds.defaults(), false, 0.0, ExemptionPolicy.none());
        SimulatedSession creative = legit("p1").withContext(SessionContext.creative());
        SimulatedSession survival = legit("p2");

        TestRun run = new Runner().execute(request, PVP, List.of(creative, survival), alwaysFlags());

        assertEquals(2, run.metrics().falsePositives());
        assertTrue(run.exemptedFlags().isEmpty());
    }

    private static SimulatedSession legit() {
        return legit("p1");
    }

    private static SimulatedSession legit(String playerId) {
        return SimulatedSession.legit(playerId, "pvp", emptyStream(), "trajectory-" + playerId);
    }

    private static SimulatedSession sessionWithJoinAge(long ticksSinceJoin) {
        SessionContext ctx = new SessionContext(io.laplace.sprite.core.model.GameMode.SURVIVAL, false,
                ticksSinceJoin, Set.of(), Set.of());
        return legit().withContext(ctx);
    }

    private static EventStream emptyStream() {
        return new InMemoryEventStream(List.of());
    }

    private static DetectionTarget alwaysFlags() {
        return new DetectionTarget() {
            @Override
            public String id() {
                return "always-flags";
            }

            @Override
            public List<FlagEvent> detect(DetectionContext ctx) {
                return List.of(FlagEvent.falsePositive("run-ex", ctx.playerId(), "sim.speed", 10,
                        "trajectory-" + ctx.playerId(), null));
            }
        };
    }
}
