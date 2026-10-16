package io.laplace.sprite.sandbox;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 沙箱启动器（15.4）：把一个 {@link SandboxSpec} 渲染成 {@code docker run} 参数向量与 compose 片段，
 * 并负责真正拉起一次性容器。
 *
 * <p>docker 不可用时只给不可用状态，不抛异常：沙箱轨缺席不该拖垮整条流水线，其余轨道照跑。
 * 渲染部分不依赖 docker，因此参数正确性可以离线断言。</p>
 */
public final class SandboxLauncher {

    public static final String DEFAULT_DOCKER_EXECUTABLE = "docker";

    /** 镜像内固定端口：服务端 25565、RCON 25575。 */
    public static final int CONTAINER_SERVER_PORT = 25565;
    public static final int CONTAINER_RCON_PORT = 25575;

    /** OOM killer 终止容器时 docker 报的退出码（128 + SIGKILL）。 */
    public static final int OOM_EXIT_CODE = 137;

    private static final long PROBE_TIMEOUT_SECONDS = 2L;

    private final String dockerExecutable;
    private final Path workingDirectory;

    public SandboxLauncher() {
        this(DEFAULT_DOCKER_EXECUTABLE, Path.of("."));
    }

    public SandboxLauncher(String dockerExecutable) {
        this(dockerExecutable, Path.of("."));
    }

    public SandboxLauncher(String dockerExecutable, Path workingDirectory) {
        this.dockerExecutable = dockerExecutable;
        this.workingDirectory = workingDirectory;
    }

    public String dockerExecutable() {
        return dockerExecutable;
    }

    /** docker 可执行文件是否可用。 */
    public boolean available() {
        try {
            Process process = new ProcessBuilder(dockerExecutable, "--version")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException ex) {
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** 每轮独立网络的创建参数：默认拒绝出站（--internal），白名单以 label 声明给宿主防火墙消费。 */
    public List<String> networkCreateArguments(SandboxNetworkPolicy policy) {
        List<String> args = new ArrayList<>();
        args.add("network");
        args.add("create");
        args.add("--driver");
        args.add("bridge");
        if (policy.denyOutbound()) {
            args.add("--internal");
        }
        args.add("--label");
        args.add("laplace.allowlist=" + policy.allowlistLabel());
        args.add(policy.networkName());
        return List.copyOf(args);
    }

    /**
     * {@code docker run} 的参数向量（不含 docker 可执行文件本身）。
     *
     * <p>只读挂载带 {@code :ro}，唯一可写卷是 spec 的 writableDataDir → /data；根文件系统本身
     * 也设 {@code --read-only}，容器内除 /data 与 /tmp 外无可写落点。</p>
     */
    public List<String> runArguments(SandboxSpec spec) {
        List<String> args = new ArrayList<>();
        args.add("run");
        args.add("--rm");
        args.add("--name");
        args.add(spec.containerName());
        args.add("--memory");
        args.add(spec.memoryMb() + "m");
        args.add("--cpus");
        args.add(formatCpus(spec.cpus()));
        args.add("--read-only");
        args.add("--tmpfs");
        args.add("/tmp");
        args.add("--network");
        args.add(spec.networkPolicy().networkName());
        args.add("--publish");
        args.add("127.0.0.1:" + spec.rconPort() + ":" + CONTAINER_RCON_PORT);
        // 服务端端口不指定宿主端口：由 docker 在 127.0.0.1 上随机分配，避免并行用例互撞
        args.add("--publish");
        args.add("127.0.0.1::" + CONTAINER_SERVER_PORT);
        for (SandboxMount mount : spec.readOnlyMounts()) {
            args.add("--volume");
            args.add(mount.volumeArgument());
        }
        args.add("--volume");
        args.add(hostPath(spec.writableDataDir()) + ":" + SandboxSpec.CONTAINER_DATA_DIR);
        args.add("--label");
        args.add("laplace.case=" + spec.caseId());
        for (Map.Entry<String, String> label : spec.labels().entrySet()) {
            args.add("--label");
            args.add(label.getKey() + "=" + label.getValue());
        }
        args.add("--env");
        args.add("EULA=TRUE");
        args.add("--env");
        args.add("TYPE=" + spec.serverType());
        args.add("--env");
        args.add("VERSION=" + spec.serverVersion());
        args.add("--env");
        args.add("MEMORY=" + spec.jvmHeapMb() + "M");
        args.add("--env");
        args.add("INIT_MEMORY=" + spec.initHeapMb() + "M");
        args.add("--env");
        args.add("ENABLE_RCON=true");
        args.add("--env");
        args.add("RCON_PORT=" + CONTAINER_RCON_PORT);
        args.add("--env");
        args.add("RCON_PASSWORD=" + spec.rconPassword());
        for (Map.Entry<String, String> env : spec.extraEnv().entrySet()) {
            args.add("--env");
            args.add(env.getKey() + "=" + env.getValue());
        }
        args.add(spec.image());
        return List.copyOf(args);
    }

    /** 与 runArguments 等价的 compose 片段（含本轮网络声明）。 */
    public String composeFragment(SandboxSpec spec) {
        StringBuilder yaml = new StringBuilder();
        yaml.append("services:\n");
        yaml.append("  ").append(spec.caseId()).append(":\n");
        yaml.append("    image: \"").append(spec.image()).append("\"\n");
        yaml.append("    container_name: \"").append(spec.containerName()).append("\"\n");
        yaml.append("    read_only: true\n");
        yaml.append("    tmpfs:\n      - /tmp\n");
        yaml.append("    mem_limit: ").append(spec.memoryMb()).append("m\n");
        yaml.append("    cpus: ").append(formatCpus(spec.cpus())).append("\n");
        // 自愈交给 OomBackoffPolicy，不使用 docker restart policy：policy 重试会让 OOM 结果难以归因
        yaml.append("    restart: \"no\"\n");
        yaml.append("    networks:\n      - ").append(spec.networkPolicy().networkName()).append("\n");
        yaml.append("    ports:\n");
        yaml.append("      - \"127.0.0.1:").append(spec.rconPort()).append(":").append(CONTAINER_RCON_PORT)
                .append("\"\n");
        yaml.append("      - \"127.0.0.1::").append(CONTAINER_SERVER_PORT).append("\"\n");
        yaml.append("    environment:\n");
        yaml.append("      EULA: \"TRUE\"\n");
        yaml.append("      TYPE: \"").append(spec.serverType()).append("\"\n");
        yaml.append("      VERSION: \"").append(spec.serverVersion()).append("\"\n");
        yaml.append("      MEMORY: \"").append(spec.jvmHeapMb()).append("M\"\n");
        yaml.append("      INIT_MEMORY: \"").append(spec.initHeapMb()).append("M\"\n");
        yaml.append("      ENABLE_RCON: \"true\"\n");
        yaml.append("      RCON_PORT: \"").append(CONTAINER_RCON_PORT).append("\"\n");
        yaml.append("      RCON_PASSWORD: \"").append(spec.rconPassword()).append("\"\n");
        for (Map.Entry<String, String> env : spec.extraEnv().entrySet()) {
            yaml.append("      ").append(env.getKey()).append(": \"").append(env.getValue()).append("\"\n");
        }
        yaml.append("    volumes:\n");
        for (SandboxMount mount : spec.readOnlyMounts()) {
            yaml.append("      - \"").append(mount.volumeArgument()).append("\"\n");
        }
        yaml.append("      - \"").append(hostPath(spec.writableDataDir())).append(":")
                .append(SandboxSpec.CONTAINER_DATA_DIR).append("\"\n");
        yaml.append("    labels:\n");
        yaml.append("      laplace.case: \"").append(spec.caseId()).append("\"\n");
        for (Map.Entry<String, String> label : spec.labels().entrySet()) {
            yaml.append("      ").append(label.getKey()).append(": \"").append(label.getValue()).append("\"\n");
        }
        yaml.append("networks:\n");
        yaml.append("  ").append(spec.networkPolicy().networkName()).append(":\n");
        yaml.append("    internal: ").append(spec.networkPolicy().denyOutbound()).append("\n");
        return yaml.toString();
    }

    /**
     * 拉起一次容器。docker 不可用时返回 unavailable，让判定落到 SKIPPED 而不是 FAILED。
     */
    public SandboxCaseRunner.ContainerOutcome runOnce(SandboxSpec spec, int attempt) {
        if (!available()) {
            return SandboxCaseRunner.ContainerOutcome.unavailable(
                    "unavailable: docker 不可用（可执行文件 %s），用例 %s 第 %d 次未启动，其余轨道不受影响"
                            .formatted(dockerExecutable, spec.caseId(), attempt));
        }
        List<String> command = new ArrayList<>();
        command.add(dockerExecutable);
        command.addAll(runArguments(spec));
        try {
            Process process = new ProcessBuilder(command)
                    .directory(workingDirectory.toFile())
                    .redirectErrorStream(true)
                    .start();
            List<String> output = process.inputReader().lines().toList();
            process.waitFor();
            int exitCode = process.exitValue();
            boolean oom = exitCode == OOM_EXIT_CODE || output.stream().anyMatch(SandboxLauncher::looksLikeOom);
            return SandboxCaseRunner.ContainerOutcome.completed(exitCode, oom, output);
        } catch (IOException ex) {
            return SandboxCaseRunner.ContainerOutcome.unavailable(
                    "unavailable: 执行 docker 失败（%s），用例 %s 未启动".formatted(ex.getMessage(), spec.caseId()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return SandboxCaseRunner.ContainerOutcome.unavailable(
                    "unavailable: 等待 docker 结束被中断，用例 %s 状态未知".formatted(spec.caseId()));
        }
    }

    private static boolean looksLikeOom(String line) {
        String lower = line.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("oom-killed") || lower.contains("outofmemoryerror") || lower.contains("oom killer");
    }

    /** docker 走正斜杠更省心（Windows 盘符同样接受 C:/path）。 */
    private static String hostPath(Path path) {
        return path.toAbsolutePath().normalize().toString().replace('\\', '/');
    }

    private static String formatCpus(double cpus) {
        return Double.toString(cpus);
    }
}
