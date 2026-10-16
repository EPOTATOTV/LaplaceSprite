package io.laplace.sprite.adapters;

import io.laplace.sprite.adapters.lifecycle.SimulatedLifecycleHook;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.model.Artifact;
import io.laplace.sprite.core.model.ArtifactRole;
import io.laplace.sprite.core.model.ArtifactSpec;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.model.RunTarget;
import io.laplace.sprite.core.model.ServerPluginConfig;
import io.laplace.sprite.core.spi.InstallContext;
import io.laplace.sprite.core.spi.LaplaceAdapter;
import io.laplace.sprite.core.spi.LifecycleHook;
import io.laplace.sprite.core.spi.TickContext;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 冒烟剧本（14.8 第 3 步）：每个适配器至少跑通三出戏。
 *
 * <p>装配成功 → 核心事件归一 → 生命周期全链路。三出戏都不需要真实服务端，进程内即可判定，
 * 因此可以作为「新平台接入」的第一道门。</p>
 */
public final class SmokeScenarios {

    /** 三出戏的固定名称。 */
    public static final List<String> REQUIRED_PLAYS = List.of("assembly", "core-event-bridge", "lifecycle");

    /** 14.6 归一化模型的核心事件类型（验收要求 ≥10 类）。 */
    private static final List<String> CORE_EVENT_TYPES = List.of(
            "PLAYER_JOIN", "PLAYER_LEAVE", "MOVE", "INTERACT", "CHAT", "INVENTORY",
            "PACKET", "CHUNK_LOAD", "BLOCK_CHANGE", "DATA_CONSISTENCY",
            "PROXY_PLAYER_JOIN", "PROXY_PLAYER_LEAVE", "PROXY_CHAT", "DISCONNECT",
            "COMBAT", "BRAND", "MOD_HASH", "ADDON", "REGISTRY", "GAME_TEST_SUITE");

    private static final int MIN_CORE_TYPES = 10;

    public record SmokeResult(String scenario, String adapterId, boolean passed, String detail) {
    }

    public List<SmokeResult> run(LaplaceAdapter adapter, PlatformKey key) {
        return List.of(assembly(adapter, key), coreEventBridge(adapter, key), lifecycle(adapter, key));
    }

    /** 第 1 出：描述符、装配目录、产物解析与依赖顺序都可用。 */
    public SmokeResult assembly(LaplaceAdapter adapter, PlatformKey key) {
        try {
            Artifact artifact = adapter.artifactResolver()
                    .resolve(ArtifactSpec.maven("io.laplace.sprite:sprite-probe:1.0.0", roleFor(adapter.family())));
            Path directory = adapter.installLayout().layoutDirectory(key);
            List<Artifact> ordered = adapter.installLayout().orderByDependencies(List.of(artifact));
            boolean passed = artifact != null && directory != null && ordered.size() == 1;
            return new SmokeResult("assembly", adapter.id(), passed,
                    "描述符 %s；装配目录 %s；产物 %s:%s".formatted(descriptorName(adapter), directory,
                            artifact == null ? "-" : artifact.id(),
                            artifact == null ? "-" : artifact.version()));
        } catch (RuntimeException ex) {
            return new SmokeResult("assembly", adapter.id(), false, "装配失败: " + ex.getMessage());
        }
    }

    /** 第 2 出：事件桥归一出 ≥10 类核心事件。 */
    public SmokeResult coreEventBridge(LaplaceAdapter adapter, PlatformKey key) {
        try {
            EventStream stream = adapter.eventBridge().bridge(key);
            List<String> covered = stream.stream()
                    .map(Event::type)
                    .map(Enum::name)
                    .distinct()
                    .filter(CORE_EVENT_TYPES::contains)
                    .toList();
            boolean passed = covered.size() >= MIN_CORE_TYPES;
            return new SmokeResult("core-event-bridge", adapter.id(), passed,
                    "核心事件归一 %d 类（要求 ≥%d）: %s".formatted(covered.size(), MIN_CORE_TYPES,
                            String.join(",", covered)));
        } catch (RuntimeException ex) {
            return new SmokeResult("core-event-bridge", adapter.id(), false, "事件桥失败: " + ex.getMessage());
        }
    }

    /** 第 3 出：冷启动 onLoad → onEnable → onTick → onShutdown，且不含 reload 语义。 */
    public SmokeResult lifecycle(LaplaceAdapter adapter, PlatformKey key) {
        try {
            Artifact artifact = adapter.artifactResolver()
                    .resolve(ArtifactSpec.maven("io.laplace.sprite:sprite-probe:1.0.0", roleFor(adapter.family())));
            InstallContext ctx = new InstallContext(key, RunTarget.detectionEngine(), Path.of("server"),
                    List.of(artifact), ServerPluginConfig.safeDefaults(), Map.of(), true, true);
            LifecycleHook hook = adapter.lifecycleHook();
            hook.onLoad(ctx);
            hook.onEnable(ctx);
            hook.onTick(new TickContext(key, 1L, Instant.now(), null));
            hook.onShutdown(ctx);
            if (hook instanceof SimulatedLifecycleHook simulated) {
                List<String> history = simulated.history();
                boolean hasReload = history.stream().anyMatch(line -> line.contains("reload"));
                boolean ordered = history.size() >= 4
                        && history.get(0).startsWith("onLoad")
                        && history.get(1).startsWith("onEnable")
                        && history.get(2).startsWith("onTick")
                        && history.get(3).startsWith("onShutdown");
                return new SmokeResult("lifecycle", adapter.id(), !hasReload && ordered,
                        "生命周期日志 %d 条，无 reload 语义: %s".formatted(history.size(),
                                String.join(" | ", history)));
            }
            return new SmokeResult("lifecycle", adapter.id(), true, "生命周期钩子执行完成（无历史接口）");
        } catch (RuntimeException ex) {
            return new SmokeResult("lifecycle", adapter.id(), false, "生命周期失败: " + ex.getMessage());
        }
    }

    /** 插件平台装 plugins/，模组加载器装 mods/，其余按数据包处理。 */
    static ArtifactRole roleFor(PlatformFamily family) {
        if (family.isModLoader()) {
            return ArtifactRole.MOD;
        }
        if (family.isPluginPlatform() || family == PlatformFamily.GEYSER) {
            return ArtifactRole.PLUGIN;
        }
        return ArtifactRole.DATAPACK;
    }

    private static String descriptorName(LaplaceAdapter adapter) {
        return adapter instanceof AbstractLaplaceAdapter base ? base.descriptorFile() : "-";
    }
}
