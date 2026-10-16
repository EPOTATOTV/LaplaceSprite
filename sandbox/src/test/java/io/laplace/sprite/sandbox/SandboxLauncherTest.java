package io.laplace.sprite.sandbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 沙箱启动器（15.4）：docker 参数渲染、只读挂载、网络隔离、docker 缺席时降级。 */
class SandboxLauncherTest {

    private static final String IMAGE = "itzg/minecraft-server:java21";

    private static SandboxSpec spec(Path tempDir) {
        return SandboxSpec.forTier("case-smoke-1", SandboxTier.SMOKE, IMAGE, "PAPER", "1.21.11",
                List.of(SandboxMount.readOnly("/host/mods", "/data/mods"),
                        SandboxMount.readOnly("/host/plugins", "/data/plugins"),
                        SandboxMount.readOnly("/host/server.jar", "/server.jar")),
                tempDir.resolve("data"), 25575);
    }

    @Test
    void runArgumentsCarryQuotaPortsAndReadOnlyMounts(@TempDir Path tempDir) {
        SandboxLauncher launcher = new SandboxLauncher("definitely-not-a-docker-binary-xyz");
        SandboxSpec spec = spec(tempDir);
        List<String> args = launcher.runArguments(spec);

        assertEquals("run", args.get(0), "参数向量从 run 开始，docker 可执行文件由调用方补");
        assertTrue(args.containsAll(List.of("--memory", "1024m")), "内存配额按等级下发");
        assertTrue(args.containsAll(List.of("--cpus", "1.0")), "CPU 配额按等级下发");
        assertTrue(args.contains("--read-only"), "根文件系统只读");
        assertEquals(List.of("--network", "laplace-sandbox-case-smoke-1"),
                sublistOf(args, "--network"), "每轮用例一个独立网络");
        assertTrue(args.contains("127.0.0.1:25575:25575"), "RCON 端口只绑 127.0.0.1");
        assertTrue(args.contains("127.0.0.1::25565"), "服务端端口只绑 127.0.0.1，宿主端口随机");
        assertTrue(args.contains("/host/mods:/data/mods:ro"), "mods 目录只读挂入");
        assertTrue(args.contains("/host/plugins:/data/plugins:ro"), "plugins 目录只读挂入");
        assertTrue(args.contains("/host/server.jar:/server.jar:ro"), "服务端 jar 只读挂入");
        assertEquals(1, args.stream().filter(arg -> arg.endsWith(":/data")).count(), "只有 /data 可写");
        assertFalse(args.stream().anyMatch(arg -> arg.endsWith(":/data:ro")), "/data 不能是只读挂载");
        assertTrue(args.contains("TYPE=PAPER"));
        assertTrue(args.contains("VERSION=1.21.11"));
        assertTrue(args.contains("MEMORY=717M"), "JVM 堆取容器内存约 70%");
        assertEquals(IMAGE, args.get(args.size() - 1));
        assertFalse(launcher.available(), "不存在的 docker 可执行文件应判为不可用");
    }

    @Test
    void extraEnvAndLabelsAreAppended(@TempDir Path tempDir) {
        SandboxSpec base = spec(tempDir);
        SandboxSpec spec = new SandboxSpec(base.caseId(), base.image(), base.serverType(),
                base.serverVersion(), base.memoryMb(), base.cpus(), base.jvmHeapMb(), base.readOnlyMounts(),
                base.writableDataDir(), base.networkPolicy(), base.rconPort(), base.worldSnapshotEnabled(),
                base.oomRestartLimit(), java.util.Map.of("OPS", "Laplace"), java.util.Map.of("laplace.tier", "smoke"));
        List<String> args = new SandboxLauncher().runArguments(spec);

        assertTrue(args.contains("OPS=Laplace"));
        assertTrue(args.contains("laplace.tier=smoke"));
        assertTrue(args.contains("laplace.case=case-smoke-1"));
    }

    @Test
    void networkCreateArgumentsDenyOutboundAndDeclareAllowlist(@TempDir Path tempDir) {
        SandboxLauncher launcher = new SandboxLauncher();
        SandboxSpec spec = spec(tempDir);
        List<String> args = launcher.networkCreateArguments(spec.networkPolicy());

        assertEquals(List.of("network", "create", "--driver", "bridge", "--internal"),
                args.subList(0, 5), "默认拒绝全部出站");
        assertTrue(args.contains("laplace.allowlist=" + spec.networkPolicy().allowlistLabel()));
        assertTrue(spec.networkPolicy().allowlistLabel().contains("libraries.minecraft.net"));
        assertTrue(spec.networkPolicy().allowlistLabel().contains("maven.fabricmc.net"));
        assertTrue(spec.networkPolicy().allowlistLabel().contains("repo.papermc.io"));
        assertEquals(spec.networkPolicy().networkName(), args.get(args.size() - 1));
        assertTrue(spec.networkPolicy().allows("repo.papermc.io"));
        assertFalse(spec.networkPolicy().allows("example.com"), "白名单外的域名不放行");
    }

    @Test
    void composeFragmentMirrorsRunArguments(@TempDir Path tempDir) {
        SandboxSpec spec = spec(tempDir);
        String yaml = new SandboxLauncher().composeFragment(spec);

        assertTrue(yaml.startsWith("services:"));
        assertTrue(yaml.contains("  case-smoke-1:"));
        assertTrue(yaml.contains("read_only: true"));
        assertTrue(yaml.contains("mem_limit: 1024m"));
        assertTrue(yaml.contains("cpus: 1.0"));
        assertTrue(yaml.contains("127.0.0.1:25575:25575"));
        assertTrue(yaml.contains("127.0.0.1::25565"));
        assertTrue(yaml.contains("/host/mods:/data/mods:ro"));
        assertTrue(yaml.contains("MEMORY: \"717M\""));
        assertTrue(yaml.contains("internal: true"));
    }

    @Test
    void dockerAbsenceDegradesToUnavailableWithoutThrowing(@TempDir Path tempDir) {
        SandboxLauncher launcher = new SandboxLauncher("definitely-not-a-docker-binary-xyz");
        SandboxCaseRunner.ContainerOutcome outcome = launcher.runOnce(spec(tempDir), 0);

        assertTrue(outcome.unavailable());
        assertFalse(outcome.succeeded());
        assertTrue(outcome.logs().stream().anyMatch(line -> line.contains("unavailable")),
                "必须给出非致命的 unavailable 说明");
    }

    @Test
    void containerNameIsDerivedFromCaseId(@TempDir Path tempDir) {
        SandboxSpec spec = spec(tempDir);
        assertEquals("laplace-sandbox-case-smoke-1", spec.containerName());
        assertNotEquals(spec.rconPassword(), "");
    }

    private static List<String> sublistOf(List<String> args, String flag) {
        int index = args.indexOf(flag);
        assertTrue(index >= 0, "缺少参数 " + flag);
        return args.subList(index, index + 2);
    }
}
