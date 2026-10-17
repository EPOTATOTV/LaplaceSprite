package io.laplace.sprite.servers;

import io.laplace.sprite.core.model.RunStatus;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSH 控制轨（12.2）：平台端独立部署后，经 SSH 驱动 8h8g 测试节点上的 docker compose。
 *
 * <p>与 {@link DockerComposeDriver} 的差别在控制通道：本驱动器不在本地起进程，而是在远端
 * {@code cd <远端工作目录> && docker compose -f <compose 文件> ...}。远端工作目录是配置，不是本地 cwd。
 * 通道不可用时状态落到 {@link DriverState#UNAVAILABLE}，不伪造远端容器状态。</p>
 */
public final class SshCommandDriver implements ServerDriver {

    public static final String DEFAULT_COMPOSE_FILE = "docker-compose.yml";

    private static final int LOG_CAPACITY = 500;

    private final SshTransport transport;
    private final String remoteWorkDirectory;
    private final String composeFile;
    private final Map<String, DriverState> states = new ConcurrentHashMap<>();
    private final Map<String, Deque<String>> logs = new ConcurrentHashMap<>();

    public SshCommandDriver(SshTransport transport, String remoteWorkDirectory, String composeFile) {
        this.transport = transport;
        this.remoteWorkDirectory = remoteWorkDirectory;
        this.composeFile = composeFile;
    }

    public SshCommandDriver(SshTransport transport, String remoteWorkDirectory) {
        this(transport, remoteWorkDirectory, DEFAULT_COMPOSE_FILE);
    }

    @Override
    public String name() {
        return "ssh";
    }

    @Override
    public boolean available() {
        return transport.available();
    }

    /**
     * 远端命令：先 cd 到配置的远端工作目录，再调 docker compose。路径一律单引号包裹，含空格也安全；
     * 实例名同样加引号传给 compose。
     */
    public String composeCommand(String... composeArgs) {
        StringBuilder command = new StringBuilder("cd ").append(quote(remoteWorkDirectory));
        command.append(" && docker compose -f ").append(quote(composeFile));
        for (String arg : composeArgs) {
            command.append(' ').append(arg);
        }
        return command.toString();
    }

    @Override
    public void start(TestServerSpec spec) {
        if (!transport.available()) {
            states.put(spec.instanceId(), DriverState.UNAVAILABLE);
            log(spec.instanceId(), "unavailable: %s，实例未启动，其余轨道不受影响".formatted(transport.describe()));
            return;
        }
        SshTransport.ExecResult result = transport.exec(composeCommand("up", "-d", quote(spec.instanceId())));
        if (result.success()) {
            states.put(spec.instanceId(), DriverState.RUNNING);
            log(spec.instanceId(), "远端 docker compose up -d 完成");
        } else {
            states.put(spec.instanceId(), DriverState.FAILED);
            log(spec.instanceId(), "远端启动失败（exit %d）: %s".formatted(result.exitCode(),
                    result.stderr().strip()));
        }
    }

    @Override
    public void stop(String instanceId) {
        if (!transport.available()) {
            states.put(instanceId, DriverState.UNAVAILABLE);
            return;
        }
        transport.exec(composeCommand("stop", quote(instanceId)));
        states.put(instanceId, DriverState.STOPPED);
        log(instanceId, "远端 docker compose stop 完成");
    }

    @Override
    public boolean running(String instanceId) {
        return states.get(instanceId) == DriverState.RUNNING;
    }

    @Override
    public List<String> tail(String instanceId, int lines) {
        if (transport.available()) {
            SshTransport.ExecResult result = transport.exec(composeCommand("logs", "--tail",
                    Integer.toString(Math.max(0, lines)), quote(instanceId)));
            if (result.success() && !result.stdout().isBlank()) {
                return result.stdout().lines().toList();
            }
        }
        return cached(instanceId, lines);
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

    /** 远端实例健康：执行 {@code docker compose ps} 取输出；通道不可用时明确说明未查询。 */
    public String health(String instanceId) {
        if (!transport.available()) {
            return "unavailable: %s，未查询远端实例 %s".formatted(transport.describe(), instanceId);
        }
        SshTransport.ExecResult result = transport.exec(composeCommand("ps", quote(instanceId)));
        return result.success() ? result.stdout().strip()
                : "查询远端状态失败（exit %d）: %s".formatted(result.exitCode(), result.stderr().strip());
    }

    /** POSIX 单引号包裹：内部单引号按 {@code ' -> '\''} 转义，远端 shell 与本地同规则。 */
    private static String quote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private List<String> cached(String instanceId, int lines) {
        Deque<String> entries = logs.getOrDefault(instanceId, new ArrayDeque<>());
        List<String> all = List.copyOf(entries);
        int from = Math.max(0, all.size() - Math.max(0, lines));
        return all.subList(from, all.size());
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
}
