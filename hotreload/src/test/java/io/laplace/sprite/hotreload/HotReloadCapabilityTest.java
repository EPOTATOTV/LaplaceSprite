package io.laplace.sprite.hotreload;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 重载能力判定（15.2）：方法体 / 签名 / 字段 / Mixin / 资源与目录改动逐条对表。 */
class HotReloadCapabilityTest {

    private final HotReloadCapability capability = new HotReloadCapability();

    @Test
    void methodBodyIsSwappableOnHotSpotButStructureIsNot() {
        HotReloadCapability.Decision body = capability.decide(JvmKind.HOTSPOT, ChangeKind.METHOD_BODY);
        assertTrue(body.hotSwappable());
        assertFalse(body.requiresRestart());
        assertTrue(body.agentArgs().isEmpty());

        HotReloadCapability.Decision signature = capability.decide(JvmKind.HOTSPOT, ChangeKind.METHOD_SIGNATURE);
        assertFalse(signature.hotSwappable());
        assertTrue(signature.requiresRestart());
        assertTrue(signature.recommendation().contains("重启"));

        assertFalse(capability.decide(JvmKind.HOTSPOT, ChangeKind.FIELD).hotSwappable());
    }

    @Test
    void enhancedRedefinitionUnlocksStructureOnDcevmAndJbr() {
        for (JvmKind jvm : List.of(JvmKind.DCEVM, JvmKind.JETBRAINS_RUNTIME)) {
            HotReloadCapability.Decision signature = capability.decide(jvm, ChangeKind.METHOD_SIGNATURE);
            assertTrue(signature.hotSwappable(), jvm + " 应支持结构改动");
            assertFalse(signature.requiresRestart());
            assertEquals(List.of(JvmProbe.ENHANCED_REDEFINITION_FLAG), signature.agentArgs());
            assertFalse(capability.decide(jvm, ChangeKind.FIELD).requiresRestart());
        }
    }

    @Test
    void mixinNeedsSpongeMixinAgent() {
        HotReloadCapability.Decision mixin = capability.decide(JvmKind.JETBRAINS_RUNTIME, ChangeKind.MIXIN);

        assertFalse(mixin.hotSwappable());
        assertTrue(mixin.requiresRestart());
        assertEquals(List.of("-javaagent:sponge-mixin.jar"), mixin.agentArgs());
        assertTrue(mixin.recommendation().contains("sponge-mixin"));
    }

    @Test
    void resourcesAndDirectoriesRouteToTheirTracks() {
        assertTrue(capability.decide(JvmKind.HOTSPOT, ChangeKind.RESOURCE_ASSETS).hotSwappable());
        assertTrue(capability.decide(JvmKind.HOTSPOT, ChangeKind.RESOURCE_DATA).recommendation()
                .contains("minecraft:reload"), "数据包走原版 reload，要写清楚");
        for (ChangeKind kind : List.of(ChangeKind.MODS_DIR, ChangeKind.PLUGIN_JAR)) {
            HotReloadCapability.Decision decision = capability.decide(JvmKind.JETBRAINS_RUNTIME, kind);
            assertFalse(decision.hotSwappable());
            assertTrue(decision.requiresRestart());
            assertTrue(decision.recommendation().contains("冷"));
        }
    }

    @Test
    void unknownJvmIsHandledConservatively() {
        HotReloadCapability.Decision body = capability.decide(JvmKind.UNKNOWN, ChangeKind.METHOD_BODY);

        assertFalse(body.hotSwappable());
        assertTrue(body.requiresRestart());
    }

    @Test
    void everyCombinationHasAConsistentVerdict() {
        for (JvmKind jvm : JvmKind.values()) {
            for (ChangeKind kind : ChangeKind.values()) {
                HotReloadCapability.Decision decision = capability.decide(jvm, kind);
                assertNotNull(decision);
                assertFalse(decision.recommendation().isBlank(), jvm + " × " + kind + " 必须给人话建议");
                assertFalse(decision.hotSwappable() && decision.requiresRestart(),
                        jvm + " × " + kind + " 的「可热生效」与「需要重启」不能同时成立");
            }
        }
    }

    @Test
    void reportRendersVerdictAndGateState() {
        HotReloadReport report = capability.evaluate("change-1", JvmKind.HOTSPOT, ChangeKind.METHOD_BODY);

        assertEquals("change-1", report.changeId());
        assertEquals(JvmKind.HOTSPOT, report.jvm());
        assertTrue(report.hotSwappable());
        assertFalse(report.marked());
        assertEquals("", report.marker());
        assertTrue(report.releaseGateAllowed());
        assertThrows(IllegalArgumentException.class,
                () -> capability.evaluate(" ", JvmKind.HOTSPOT, ChangeKind.METHOD_BODY));
    }
}
