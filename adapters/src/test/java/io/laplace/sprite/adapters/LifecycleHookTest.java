package io.laplace.sprite.adapters;

import io.laplace.sprite.adapters.lifecycle.SimulatedLifecycleHook;
import io.laplace.sprite.adapters.matrix.VersionMatrixRegistry;
import io.laplace.sprite.core.model.Artifact;
import io.laplace.sprite.core.model.ArtifactDependency;
import io.laplace.sprite.core.model.ArtifactRole;
import io.laplace.sprite.core.model.ArtifactSource;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.model.RunTarget;
import io.laplace.sprite.core.model.ServerPluginConfig;
import io.laplace.sprite.core.spi.InstallContext;
import io.laplace.sprite.core.spi.TickContext;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 冷启动生命周期（14.4 ③ / 14.5）。 */
class LifecycleHookTest {

    private static final PlatformKey PAPER = PlatformKey.of(PlatformFamily.PAPER, "1.21.11");

    @Test
    void runsOnLoadEnableTickShutdownInOrder() {
        SimulatedLifecycleHook hook = new SimulatedLifecycleHook();
        InstallContext ctx = ctx(PAPER, List.of(artifact("probe", List.of())));
        hook.onLoad(ctx);
        hook.onEnable(ctx);
        hook.onTick(new TickContext(PAPER, 1L, Instant.now(), null));
        hook.onShutdown(ctx);

        List<String> history = hook.history();
        assertEquals(4, history.size(), history.toString());
        assertTrue(history.get(0).startsWith("onLoad 校验通过"), history.get(0));
        assertTrue(history.get(1).startsWith("onEnable"), history.get(1));
        assertTrue(history.get(2).startsWith("onTick"), history.get(2));
        assertTrue(history.get(3).startsWith("onShutdown"), history.get(3));
        assertFalse(history.stream().anyMatch(line -> line.contains("reload")), "不应有 reload 语义");
        assertFalse(hook.loaded(), "停机后回到未装载状态");
    }

    @Test
    void failsWhenHardDependencyIsMissingAndBlocksEnable() {
        SimulatedLifecycleHook hook = new SimulatedLifecycleHook();
        InstallContext ctx = ctx(PAPER, List.of(artifact("probe", List.of(ArtifactDependency.hard("ghost")))));
        assertThrows(IllegalStateException.class, () -> hook.onLoad(ctx));
        assertTrue(hook.history().stream().anyMatch(line -> line.startsWith("onLoad 校验失败")));
        assertFalse(hook.loaded());
        assertThrows(IllegalStateException.class, () -> hook.onEnable(ctx));
    }

    @Test
    void failsWhenVersionMatrixDoesNotSupportPlatform() {
        SimulatedLifecycleHook hook = new SimulatedLifecycleHook(VersionMatrixRegistry.defaults());
        InstallContext ctx = ctx(new PlatformKey(PlatformFamily.FABRIC, "1.99.0", "0.19.5"),
                List.of(artifact("probe", List.of())));
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> hook.onLoad(ctx));
        assertTrue(error.getMessage().contains("版本矩阵"), error.getMessage());
    }

    @Test
    void loadsWhenMatrixSupportsPlatform() {
        SimulatedLifecycleHook hook = new SimulatedLifecycleHook(VersionMatrixRegistry.defaults());
        InstallContext ctx = ctx(new PlatformKey(PlatformFamily.FABRIC, "1.21.11", "0.19.5"),
                List.of(artifact("probe", List.of())));
        hook.onLoad(ctx);
        assertTrue(hook.loaded());
        assertEquals(1, hook.history().size());
    }

    @Test
    void onTickIsIgnoredBeforeLoad() {
        SimulatedLifecycleHook hook = new SimulatedLifecycleHook();
        hook.onTick(new TickContext(PAPER, 1L, Instant.now(), null));
        assertTrue(hook.history().isEmpty());
    }

    private static InstallContext ctx(PlatformKey key, List<Artifact> artifacts) {
        return new InstallContext(key, RunTarget.detectionEngine(), Path.of("server"), artifacts,
                ServerPluginConfig.safeDefaults(), Map.of(), true, true);
    }

    private static Artifact artifact(String id, List<ArtifactDependency> dependencies) {
        return new Artifact(id, id, "1.0.0", ArtifactSource.MAVEN, "io.laplace.test:" + id + ":1.0.0", null,
                ArtifactRole.PLUGIN, dependencies, Map.of());
    }
}
