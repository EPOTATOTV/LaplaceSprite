package io.laplace.sprite.cli;

import io.laplace.sprite.sandbox.ArtifactScanner;
import io.laplace.sprite.sandbox.OomBackoffPolicy;
import io.laplace.sprite.sandbox.SandboxAudit;
import io.laplace.sprite.sandbox.SandboxAuditor;
import io.laplace.sprite.sandbox.SandboxCaseRunner;
import io.laplace.sprite.sandbox.SandboxLauncher;
import io.laplace.sprite.sandbox.SandboxPathGuard;
import io.laplace.sprite.sandbox.SandboxRunResult;
import io.laplace.sprite.sandbox.SandboxSpec;
import io.laplace.sprite.sandbox.SandboxTier;
import io.laplace.sprite.sandbox.WorldSnapshotStore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * {@code laplace sandbox}：15.4 沙箱轨。
 *
 * <p>默认只渲染容器参数并做敏感路径审计（dry-run）；真正拉起容器需要显式加 {@code --execute}
 * 且本机 docker 可用，否则只报告「docker 不可用」，不假装跑过。</p>
 */
@Command(name = "sandbox",
        description = "沙箱轨：一次性容器参数渲染、敏感路径审计、世界快照与 OOM 自愈",
        mixinStandardHelpOptions = true)
final class SandboxCommand implements Callable<Integer> {

    @Option(names = "--case", defaultValue = "case-1", description = "用例 id")
    String caseId;

    @Option(names = "--tier", defaultValue = "STANDARD", description = "测试等级配额")
    SandboxTier tier;

    @Option(names = "--image", defaultValue = "itzg/minecraft-server:java21", description = "容器镜像")
    String image;

    @Option(names = "--type", defaultValue = "PAPER", description = "服务端类型（itzg TYPE）")
    String serverType;

    @Option(names = "--version", defaultValue = "1.21.11", description = "服务端版本")
    String serverVersion;

    @Option(names = "--docker", defaultValue = "docker", description = "docker 可执行文件")
    String dockerExecutable;

    @Option(names = "--mount", description = "只读挂载（host=container 形式），可重复")
    List<String> readOnlyMounts;

    @Option(names = "--execute", description = "真正拉起容器（默认只渲染）")
    boolean execute;

    @Option(names = "--scan-artifact", paramLabel = "<jar>",
            description = "只扫描 jar/zip 制品（路径逃逸、体积、可执行头与可疑特征），不渲染沙箱")
    Path scanArtifact;

    @Option(names = "--allowed-root", paramLabel = "<dir>",
            description = "扫描制品允许的根目录，可重复；给出时先过路径闸门")
    List<Path> allowedRoots;

    @Option(names = "--out", description = "产物目录，默认 <workspace>/sandbox")
    Path out;

    @Option(names = "--workspace", description = "工作目录，默认 build/laplace")
    Path workspace;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        var writer = spec.commandLine().getOut();
        if (scanArtifact != null) {
            return scan(writer, scanArtifact, allowedRoots);
        }

        Path ws = Support.workspace(workspace);
        Path dir = out != null ? out : ws.resolve("sandbox");

        var mounts = (readOnlyMounts == null ? List.<String>of() : readOnlyMounts).stream()
                .map(SandboxCommand::parseMount)
                .toList();
        Path dataDir = dir.resolve(caseId).resolve("data");
        createDirectories(dataDir);

        SandboxSpec sandbox = SandboxSpec.forTier(caseId, tier, image, serverType, serverVersion,
                mounts, dataDir, 25575);

        writer.println("沙箱轨（15.4） · 用例 " + sandbox.caseId() + " · 等级 " + tier);
        writer.printf("  配额 内存 %d MB · CPU %.1f · JVM 堆 %d MB（上限约 70%%）· OOM 重启上限 %d%n",
                sandbox.memoryMb(), sandbox.cpus(), sandbox.jvmHeapMb(), sandbox.oomRestartLimit());
        writer.println("  可写目录 " + sandbox.writableDataDir() + " · RCON " + sandbox.rconPort()
                + " · 网络策略 " + sandbox.networkPolicy().networkName());

        SandboxAuditor auditor = new SandboxAuditor();
        List<SandboxAudit> sensitive = auditor.sensitive(sandbox);
        if (!sensitive.isEmpty()) {
            writer.println("  [敏感路径审计] 拒绝启动：");
            sensitive.forEach(finding -> writer.println("    · " + finding.path() + "（" + finding.reason() + "）"));
            return 1;
        }
        writer.println("  [敏感路径审计] 通过（未绑定宿主敏感路径）");

        SandboxLauncher launcher = new SandboxLauncher(dockerExecutable);
        writer.println("  [网络] " + String.join(" ", launcher.networkCreateArguments(sandbox.networkPolicy())));
        writer.println("  [容器] " + String.join(" ", launcher.runArguments(sandbox)));
        write(dir.resolve("docker-compose.sandbox.yml"), launcher.composeFragment(sandbox));
        writer.println("  [编排] " + dir.resolve("docker-compose.sandbox.yml").toAbsolutePath());

        if (!launcher.available()) {
            writer.println("  [执行] docker 不可用，仅渲染参数（未拉起容器）");
            return 0;
        }
        if (!execute) {
            writer.println("  [执行] dry-run；加 --execute 才会真正拉起容器");
            return 0;
        }

        SandboxCaseRunner runner = new SandboxCaseRunner(auditor,
                new WorldSnapshotStore(ws.resolve("sandbox").resolve("worlds")),
                OomBackoffPolicy.defaults(), launcher::runOnce, SandboxCaseRunner.realSleeper());
        SandboxRunResult result = runner.run(sandbox);
        writer.printf("  [结果] %s · OOM 被杀 %s · 重启 %d 次 · 世界恢复 %s%n", result.status(),
                result.oomKilled(), result.restarts(), result.worldRestored());
        result.logs().forEach(line -> writer.println("    | " + line));
        return result.passed() ? 0 : 1;
    }

    private static int scan(PrintWriter writer, Path artifact, List<Path> allowedRoots) {
        try {
            if (allowedRoots != null && !allowedRoots.isEmpty()) {
                new SandboxPathGuard().checkRead(artifact, allowedRoots);
            }
            ArtifactScanner.ScanResult result = new ArtifactScanner().scan(artifact);
            writer.printf("制品扫描 %s · clean=%s · findings=%d · entries=%d · bytes=%d%n",
                    artifact.toAbsolutePath(), result.clean(), result.findings().size(),
                    result.entries(), result.uncompressedBytes());
            result.findings().forEach(finding -> writer.println("    · " + finding));
            return result.clean() ? 0 : 1;
        } catch (SecurityException ex) {
            writer.println("  [路径闸门] " + ex.getMessage());
            return 1;
        } catch (IOException ex) {
            writer.println("  [扫描失败] " + ex.getMessage());
            return 1;
        }
    }

    private static io.laplace.sprite.sandbox.SandboxMount parseMount(String raw) {
        String[] parts = raw.split("=", 2);
        return parts.length == 2
                ? io.laplace.sprite.sandbox.SandboxMount.readOnly(parts[0], parts[1])
                : io.laplace.sprite.sandbox.SandboxMount.readOnly(parts[0], parts[0]);
    }

    private static void createDirectories(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void write(Path target, String content) {
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
