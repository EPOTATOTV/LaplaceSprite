package io.laplace.sprite.servers;

import com.fasterxml.jackson.databind.JsonNode;

import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.core.model.RunStatus;
import io.laplace.sprite.servers.DockerApiTransport.ApiRequest;
import io.laplace.sprite.servers.DockerApiTransport.ApiResponse;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Docker API 控制轨（12.2）：平台端独立部署后，直接调测试节点的 Docker Engine HTTP API，不经 SSH。
 *
 * <p>渲染并下发真实 API：{@code POST /networks/create}、{@code POST /containers/create}、
 * {@code POST /containers/{id}/start}、{@code POST /containers/{id}/stop}、{@code GET /containers/{id}/json}、
 * {@code GET /containers/{id}/logs}。端口一律绑到 127.0.0.1，{@link ServerMount} 一律以只读 Binds 下发。</p>
 *
 * <p>API 版本前缀由 {@link DockerApiTransport#endpoint()} 给出。端口与挂载的语义与
 * {@link DockerComposeDriver} 保持一致，因此 {@link TestServerOrchestrator} 可原样使用。</p>
 */
public final class DockerApiDriver implements ServerDriver {

    public static final String DEFAULT_IMAGE = "itzg/minecraft-server:java21";

    private static final int LOG_CAPACITY = 500;

    private final DockerApiTransport transport;
    private final Map<String, DriverState> states = new ConcurrentHashMap<>();
    private final Map<String, String> containerIds = new ConcurrentHashMap<>();
    private final Map<String, Deque<String>> logs = new ConcurrentHashMap<>();

    public DockerApiDriver(DockerApiTransport transport) {
        this.transport = transport;
    }

    @Override
    public String name() {
        return "docker-api";
    }

    @Override
    public boolean available() {
        return transport.available();
    }

    @Override
    public void start(TestServerSpec spec) {
        if (!transport.available()) {
            states.put(spec.instanceId(), DriverState.UNAVAILABLE);
            log(spec.instanceId(), "unavailable: %s，实例未创建，其余轨道不受影响".formatted(transport.describe()));
            return;
        }
        try {
            ApiResponse network = send("POST", "/networks/create",
                    Json.write(Map.of("Name", networkName(spec.instanceId()), "Driver", "bridge",
                            "Internal", false)), true);
            // 网络已存在时 docker 返回 409，不算失败
            if (!network.successful() && network.status() != 409) {
                states.put(spec.instanceId(), DriverState.FAILED);
                log(spec.instanceId(), "创建网络失败：HTTP " + network.status());
                return;
            }

            ApiResponse created = send("POST", "/containers/create?name=" + spec.instanceId(),
                    createBody(spec), true);
            if (!created.successful()) {
                states.put(spec.instanceId(), DriverState.FAILED);
                log(spec.instanceId(), "创建容器失败：HTTP " + created.status());
                return;
            }
            String containerId = readTree(created.body()).path("Id").asText("");
            containerIds.put(spec.instanceId(), containerId);

            ApiResponse started = send("POST", "/containers/" + containerId + "/start", "", false);
            // 304 表示容器已在运行，等价成功
            if (started.successful() || started.status() == 304) {
                states.put(spec.instanceId(), DriverState.RUNNING);
                log(spec.instanceId(), "容器已启动: " + containerId);
            } else {
                states.put(spec.instanceId(), DriverState.FAILED);
                log(spec.instanceId(), "启动容器失败：HTTP " + started.status());
            }
        } catch (RuntimeException ex) {
            states.put(spec.instanceId(), DriverState.FAILED);
            log(spec.instanceId(), "Docker API 调用失败：" + ex.getMessage());
        }
    }

    @Override
    public void stop(String instanceId) {
        if (!transport.available()) {
            states.put(instanceId, DriverState.UNAVAILABLE);
            return;
        }
        String containerId = containerIds.get(instanceId);
        if (containerId == null) {
            states.put(instanceId, DriverState.STOPPED);
            log(instanceId, "无容器记录，视为已停机");
            return;
        }
        try {
            ApiResponse response = send("POST", "/containers/" + containerId + "/stop", "", false);
            states.put(instanceId, DriverState.STOPPED);
            log(instanceId, "容器停机指令已下发（HTTP %d）".formatted(response.status()));
        } catch (RuntimeException ex) {
            states.put(instanceId, DriverState.FAILED);
            log(instanceId, "停机失败：" + ex.getMessage());
        }
    }

    @Override
    public boolean running(String instanceId) {
        return states.get(instanceId) == DriverState.RUNNING;
    }

    /**
     * 远端日志：GET /containers/{id}/logs。真实守护进程在非 TTY 下返回多路复用帧，这里按纯文本行处理，
     * 需要逐字节精确解析时应在传输层解帧。
     */
    @Override
    public List<String> tail(String instanceId, int lines) {
        String containerId = containerIds.get(instanceId);
        if (transport.available() && containerId != null) {
            try {
                ApiResponse response = send("GET", "/containers/" + containerId
                        + "/logs?stdout=1&stderr=1&tail=" + Math.max(0, lines), "", false);
                if (response.successful() && !response.body().isBlank()) {
                    return response.body().lines().toList();
                }
            } catch (RuntimeException ex) {
                log(instanceId, "读取远端日志失败：" + ex.getMessage());
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

    /** 远端容器状态：GET /containers/{id}/json 取 State.Status；未执行时明确说明。 */
    public String health(String instanceId) {
        if (!transport.available()) {
            return "unavailable: %s，未查询容器 %s".formatted(transport.describe(), instanceId);
        }
        String containerId = containerIds.get(instanceId);
        if (containerId == null) {
            return "容器尚未创建：" + instanceId;
        }
        try {
            ApiResponse response = send("GET", "/containers/" + containerId + "/json", "", false);
            if (!response.successful()) {
                return "查询容器状态失败：HTTP " + response.status();
            }
            return "State.Status=" + readTree(response.body()).path("State").path("Status").asText("-");
        } catch (RuntimeException ex) {
            return "查询容器状态失败：" + ex.getMessage();
        }
    }

    /** 容器创建体：镜像 / 环境 / 只读绑定 / 端口绑到 127.0.0.1。 */
    private String createBody(TestServerSpec spec) {
        List<String> binds = new ArrayList<>();
        for (ServerMount mount : spec.mounts()) {
            binds.add(mount.volumeArgument());
        }
        Map<String, Object> portBindings = new LinkedHashMap<>();
        portBindings.put(spec.port() + "/tcp",
                List.of(Map.of("HostIp", "127.0.0.1", "HostPort", Integer.toString(spec.port()))));

        Map<String, Object> hostConfig = new LinkedHashMap<>();
        hostConfig.put("Memory", (long) spec.allocatedMemoryMb() * 1024 * 1024);
        hostConfig.put("Binds", binds);
        hostConfig.put("PortBindings", portBindings);

        Map<String, Object> exposed = new LinkedHashMap<>();
        exposed.put(spec.port() + "/tcp", Map.of());

        Map<String, Object> create = new LinkedHashMap<>();
        create.put("Image", spec.dockerImage() == null || spec.dockerImage().isBlank()
                ? DEFAULT_IMAGE : spec.dockerImage());
        create.put("Env", environmentOf(spec));
        create.put("ExposedPorts", exposed);
        create.put("HostConfig", hostConfig);
        return Json.write(create);
    }

    private static List<String> environmentOf(TestServerSpec spec) {
        List<String> env = new ArrayList<>();
        env.add("EULA=" + (spec.eulaAccepted() ? "TRUE" : "FALSE"));
        env.add("TYPE=" + spec.platform().family().name());
        env.add("VERSION=" + spec.platform().minecraftVersion());
        env.add("MEMORY=" + spec.allocatedMemoryMb() + "M");
        if (spec.pluginVersion() != null && !spec.pluginVersion().isBlank()) {
            env.add("PLUGIN_VERSION=" + spec.pluginVersion());
        }
        return env;
    }

    private ApiResponse send(String method, String route, String body, boolean json) {
        Map<String, String> headers = json ? Map.of("Content-Type", "application/json") : Map.of();
        return transport.send(new ApiRequest(method, transport.endpoint().basePath() + route, headers, body));
    }

    private static JsonNode readTree(String json) {
        try {
            return Json.jsonMapper().readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (IOException ex) {
            throw new IllegalStateException("解析 Docker API 响应失败: " + ex.getMessage(), ex);
        }
    }

    private static String networkName(String instanceId) {
        return "laplace-" + instanceId;
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
