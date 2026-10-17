package io.laplace.sprite.mixin;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 注入计数硬门禁（15.11 P0）：JVM 参数必须精确。 */
class MixinJvmFlagsTest {

    @Test
    void ciDefaultRendersExactCountingAndExportFlags() {
        MixinJvmFlags flags = MixinJvmFlags.ciDefault();

        assertEquals(List.of("-Dmixin.debug.countInjections=true", "-Dmixin.debug.export=true"),
                flags.arguments());
        assertEquals("-Dmixin.debug.countInjections=true -Dmixin.debug.export=true", flags.render());
        assertTrue(flags.countInjections());
        assertTrue(flags.export());
    }

    @Test
    void configuredAgentJarAddsJavaAgentFlag() {
        MixinJvmFlags flags = MixinJvmFlags.withAgent("libs/sponge-mixin.jar");

        assertEquals(List.of("-Dmixin.debug.countInjections=true", "-Dmixin.debug.export=true",
                "-javaagent:libs/sponge-mixin.jar"), flags.arguments());
        assertTrue(flags.render().contains("-javaagent:libs/sponge-mixin.jar"));
    }

    @Test
    void blankAgentIsIgnored() {
        assertTrue(new MixinJvmFlags(true, true, "  ").arguments().stream()
                .noneMatch(argument -> argument.startsWith("-javaagent:")));
    }
}
