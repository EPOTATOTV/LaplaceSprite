package io.laplace.sprite.mixin;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 启动日志扫描（15.11 P0）：三类真实错误形态。 */
class LaunchLogScannerTest {

    @Test
    void invalidInjectionExceptionIsParsed() {
        String log = "[main/ERROR] [mixin/]: org.spongepowered.asm.mixin.injection.throwables."
                + "InvalidInjectionException: Critical injection failure: @Inject annotation on onTick "
                + "could not find any targets matching 'tick' in net.minecraft.world.entity.Entity. "
                + "Using refmap questmod-refmap.json [PREINJECT Applicator Phase -> "
                + "questmod.mixins.json:EntityMixin -> Prepare Injections]";

        List<InjectionFailure> failures = LaunchLogScanner.scan(log);

        assertEquals(1, failures.size());
        InjectionFailure failure = failures.get(0);
        assertEquals("questmod", failure.modId());
        assertEquals("net.minecraft.world.entity.Entity", failure.targetClass());
        assertEquals(MixinInjectionKind.INJECT, failure.kind());
        assertTrue(failure.reason().contains("InvalidInjectionException"), failure.reason());
    }

    @Test
    void invalidInjectionPointIsParsedFromDescriptor() {
        String log = "[mymod.mixins.json:RenderMixin] Injection point "
                + "(Lnet/minecraft/client/Minecraft;tick()V) is not valid for @Redirect at "
                + "net.minecraft.client.Minecraft.tick (mymod.mixins.json:RenderMixin)";

        List<InjectionFailure> failures = LaunchLogScanner.scan(log);

        assertEquals(1, failures.size());
        InjectionFailure failure = failures.get(0);
        assertEquals("mymod", failure.modId());
        assertEquals("net.minecraft.client.Minecraft", failure.targetClass());
        assertEquals(MixinInjectionKind.REDIRECT, failure.kind());
        assertTrue(failure.reason().contains("注入点不匹配"), failure.reason());
    }

    @Test
    void cannotBeAppliedIsParsedWithoutMixinsConfigName() {
        String log = "[main/ERROR] [mixin/]: @Inject annotation on example cannot be applied to "
                + "non-static method net.minecraft.world.Foo.bar";

        List<InjectionFailure> failures = LaunchLogScanner.scan(log);

        assertEquals(1, failures.size());
        assertEquals(InjectionFailure.UNKNOWN, failures.get(0).modId());
        assertEquals("net.minecraft.world.Foo", failures.get(0).targetClass());
        assertTrue(failures.get(0).reason().contains("无法应用"), failures.get(0).reason());
    }

    @Test
    void ordinaryMixinInfoLinesAreIgnored() {
        String log = "[main/INFO] [mixin/]: SpongePowered MIXIN Subsystem Version=0.8.5 Source=launch\n"
                + "[main/INFO] [mixin/]: Mixin config questmod.mixins.json successfully applied";

        assertTrue(LaunchLogScanner.scan(log).isEmpty());
    }
}
