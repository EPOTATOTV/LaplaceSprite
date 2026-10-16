package io.laplace.sprite.servers;

import io.laplace.sprite.core.model.RunStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 容器轨驱动器（12.1 / 12.2）：按实例集生成 docker-compose.yml 并调用 {@code docker compose}。
 *
 * <p>docker 不存在时给出明确的 unavailable 状态并保持非致命——环境不具备不该让整条流水线失败，
 * 编排控制台可以继续跑开发轨与本地进程轨。</p>
 */
public final class DockerComposeDriver implements ServerDriver {

    public static final String DEFAULT_COMPOSE_FILE = "docker-compose.yml";

    private static final int LOG_CAPACITY = 500;
    private static final long PROBE_TIMEOUT_SECONDS = 2L;

    private final String dockerExecutable;
    private final Path workingDirectory;
    private final Map<String, TestServerSpec> registered = new ConcurrentHashMap<>();
    private final Map<String, DriverState> states = new ConcurrentHashMap<>();
    private final Map<String, Deque<String>> logs = new ConcurrentHashMap<>();

    public DockerComposeDriver() {
        this("docker", Path.of("."));
    }

    public DockerComposeDriver(String dockerExecutable) {
        this(dockerExecutable, Path.of("."));
    }

    public DockerComposeDriver(String dockerExecutable, Path workingDirectory) {
        this.dockerExecutable = dockerExecutable;
        this.workingDirectory = workingDirectory;
    }

    @Override
    public String name() {
        return "docker-compose";
    }

    /** docker 可执行文件是否可用；每次询问都重新探测成本高，这里只探一次。 */
    @Override
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

    @Override
    public void start(TestServerSpec spec) {
        registered.put(spec.instanceId(), spec);
        if (!available()) {
            states.put(spec.instanceId(), DriverState.UNAVAILABLE);
            log(spec.instanceId(), "unavailable: docker 不可用（可执行文件 %s），实例未启动，其余轨道不受影响"
                    .formatted(dockerExecutable));
            return;
        }
        Path composeFile = composeFile();
        writeCompose(composeFile, registered.values());
        if (exec(List.of("compose", "-f", composeFile.toString(), "up", "-d", spec.instanceId()))) {
            states.put(spec.instanceId(), DriverState.RUNNING);
            log(spec.instanceId(), "docker compose up -d 完成");
        } else {
            states.put(spec.instanceId(), DriverState.FAILED);
            log(spec.instanceId(), "docker compose up -d 失败");
        }
    }

    @Override
    public void stop(String instanceId) {
        if (!available()) {
            states.put(instanceId, DriverState.UNAVAILABLE);
            return;
        }
        exec(List.of("compose", "-f", composeFile().toString(), "stop", instanceId));
        states.put(instanceId, DriverState.STOPPED);
        log(instanceId, "docker compose stop 完成");
    }

    @Override
    public boolean running(String instanceId) {
        return states.get(instanceId) == DriverState.RUNNING;
    }

    @Override
    public List<String> tail(String instanceId, int lines) {
        if (available()) {
            exec(List.of("compose", "-f", composeFile().toString(), "logs", "--tail",
                    Integer.toString(Math.max(0, lines)), instanceId));
        }
        Deque<String> entries = logs.getOrDefault(instanceId, new ArrayDeque<>());
        List<String> all = List.copyOf(entries);
        int from = Math.max(0, all.size() - Math.max(0, lines));
        return all.subList(from, all.size());
    }

    @Override
    public RunStatus status(String instanceId) {
        DriverState state = states.getOrDefault(instanceId, DriverState.UNAVAILABLE);
        return switch (state) {
            case RUNNING -> RunStatus.RUNNING;
            case STOPPED -> RunStatus.ABORTED;
            case FAILED -> RunStatus.FAILED;
            case UNAVAILABLE -> RunStatus.SKIPPED;
        };
    }

    @Override
    public DriverState state(String instanceId) {
        return states.getOrDefault(instanceId, DriverState.UNAVAILABLE);
    }

    public Path composeFile() {
        return workingDirectory.resolve(DEFAULT_COMPOSE_FILE);
    }

    /** 生成 docker-compose.yml 文本（实例集 → services）。 */
    public String composeContent(Collection<TestServerSpec> specs) {
        StringBuilder yaml = new StringBuilder("services:\n");
        List<TestServerSpec> ordered = new ArrayList<>(specs);
        ordered.sort((a, b) -> a.instanceId().compareTo(b.instanceId()));
        Map<String, TestServerSpec> byId = new LinkedHashMap<>();
        ordered.forEach(spec -> byId.put(spec.instanceId(), spec));
        for (TestServerSpec spec : byId.values()) {
            yaml.append("  ").append(spec.instanceId()).append(":\n");
            yaml.append("    image: \"").append(orDefault(spec.dockerImage(), "itzg/minecraft-server:java21"))
                    .append("\"\n");
            yaml.append("    stdin_open: true\n");
            yaml.append("    tty: true\n");
            yaml.append("    environment:\n");
            yaml.append("      EULA: \"").append(spec.eulaAccepted() ? "TRUE" : "FALSE").append("\"\n");
            yaml.append("      TYPE: \"").append(spec.platform().family().name()).append("\"\n");
            yaml.append("      VERSION: \"").append(spec.platform().minecraftVersion()).append("\"\n");
            yaml.append("      MEMORY: \"").append(spec.allocatedMemoryMb()).append("M\"\n");
            yaml.append("      PLUGIN_VERSION: \"").append(orDefault(spec.pluginVersion(), "unknown"))
                    .append("\"\n");
            yaml.append("      JVM_EXTRA_OPTS: \"").append(String.join(" ", spec.jvmArgs())).append("\"\n");
            if (!spec.headless()) {
                yaml.append("      GUI: \"true\"\n");
            }
            yaml.append("    ports:\n");
            yaml.append("      - \"").append(spec.port()).append(":").append(spec.port()).append("\"\n");
            if (!spec.mounts().isEmpty()) {
                // 混合端要同时挂 plugins/ 与 mods/，一律只读
                yaml.append("    volumes:\n");
                for (ServerMount mount : spec.mounts()) {
                    yaml.append("      - \"").append(mount.volumeArgument()).append("\"\n");
                }
            }
        }
        return yaml.toString();
    }

    public void writeCompose(Path target, Collection<TestServerSpec> specs) {
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.writeString(target, composeContent(specs), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("写入 docker-compose.yml 失败: " + target, ex);
        }
    }

    private boolean exec(List<String> arguments) {
        List<String> command = new ArrayList<>();
        command.add(dockerExecutable);
        command.addAll(arguments);
        try {
            Process process = new ProcessBuilder(command)
                    .directory(workingDirectory.toFile())
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(PROBE_TIMEOUT_SECONDS * 5, TimeUnit.SECONDS);
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

    private void log(String instanceId, String line) {
        Deque<String> entries = logs.computeIfAbsent(instanceId, key -> new ArrayDeque<>());
        synchronized (entries) {
            entries.addLast(line);
            while (entries.size() > LOG_CAPACITY) {
                entries.removeFirst();
            }
        }
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
