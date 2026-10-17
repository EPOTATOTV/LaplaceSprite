package io.laplace.sprite.mixin;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 注入计数门禁（15.11 P0）：require 精确、expect 只告警。 */
class InjectionGateTest {

    @Test
    void exactCountPasses() {
        InjectionDeclaration declaration = declaration(MixinRequireMode.REQUIRE);

        InjectionGateResult result = InjectionGate.evaluate(List.of(declaration), item -> 2);

        assertTrue(result.passed());
        assertFalse(result.blocked());
        assertTrue(result.failures().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void requireMismatchFailsWithChineseReason() {
        InjectionDeclaration declaration = declaration(MixinRequireMode.REQUIRE);

        InjectionGateResult result = InjectionGate.evaluate(List.of(declaration), item -> 1);

        assertFalse(result.passed());
        assertTrue(result.blocked());
        assertEquals(1, result.failures().size());
        InjectionFailure failure = result.failures().get(0);
        assertEquals("questmod", failure.modId());
        assertEquals("net.minecraft.world.entity.Entity", failure.targetClass());
        assertEquals(MixinInjectionKind.INJECT, failure.kind());
        assertTrue(failure.reason().contains("注入点数量与声明不符"), failure.reason());
        assertTrue(failure.reason().contains("require"), failure.reason());
    }

    @Test
    void expectMismatchOnlyWarns() {
        InjectionDeclaration declaration = declaration(MixinRequireMode.EXPECT);

        InjectionGateResult result = InjectionGate.evaluate(List.of(declaration), item -> 1);

        assertTrue(result.passed(), "expect 是软约束，不能阻塞门禁");
        assertTrue(result.failures().isEmpty());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("expect"), result.warnings().get(0));
    }

    @Test
    void launchLogFailureBlocksGate() {
        String log = "[main/ERROR] [mixin/]: org.spongepowered.asm.mixin.injection.throwables."
                + "InvalidInjectionException: Critical injection failure: @Inject annotation on onTick "
                + "could not find any targets matching 'tick' in net.minecraft.world.entity.Entity. "
                + "Using refmap questmod-refmap.json [PREINJECT Applicator Phase -> "
                + "questmod.mixins.json:EntityMixin -> Prepare Injections]";

        InjectionGateResult result = InjectionGate.fromLaunchLog(log);

        assertTrue(result.blocked());
        assertEquals(1, result.failures().size());
        assertEquals("questmod", result.failures().get(0).modId());
        assertTrue(result.failures().get(0).line().contains("InvalidInjectionException"));
    }

    private static InjectionDeclaration declaration(MixinRequireMode mode) {
        return new InjectionDeclaration("questmod", "net.minecraft.world.entity.Entity",
                MixinInjectionKind.INJECT, 2, mode, "tick", "");
    }
}
